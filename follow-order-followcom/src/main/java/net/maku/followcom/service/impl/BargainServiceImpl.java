package net.maku.followcom.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.dto.MasOrderSendDto;
import net.maku.followcom.entity.*;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.enums.FollowInstructEnum;
import net.maku.followcom.service.*;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.util.RestUtil;
import net.maku.followcom.vo.FollowMasOrderVo;
import net.maku.framework.common.exception.ServerException;
import net.maku.framework.common.utils.RandomStringUtil;
import net.maku.framework.common.utils.Result;
import net.maku.framework.common.utils.ThreadPoolUtils;
import net.maku.framework.security.user.SecurityUser;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import net.maku.followcom.dto.MasToSubOrderSendDto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static net.maku.followcom.util.RestUtil.sendRequest;

/**
 * Author:  zsd
 * Date:  2025/2/25/周二 17:18
 */
@Service
@AllArgsConstructor
@Slf4j
public class BargainServiceImpl implements BargainService {

    private final FollowVpsService followVpsService;
    private final FollowTraderUserService followTraderUserService;
    private final FollowTraderService followTraderService;
    private final FollowOrderInstructService followOrderInstructService;
    private final FollowOrderInstructSubService followOrderInstructSubService;
    @Override
    public void masOrderSend(MasOrderSendDto vo, HttpServletRequest request) {
        if (vo.getStartSize().compareTo(vo.getEndSize())>0) {
            throw new ServerException("开始手数不能大于结束手数");
        }
        //现在找出可下单账号
        List<FollowTraderEntity> followTraderEntityList=new ArrayList<>();
        vo.getTraderList().forEach(o->{
            FollowTraderUserEntity followTraderUserEntity = followTraderUserService.getById(o);
            //查询各VPS状态正常的账号
            List<FollowTraderEntity> followTraderEntity = followTraderService.list(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getStatus, CloseOrOpenEnum.CLOSE.getValue()).eq(FollowTraderEntity::getAccount, followTraderUserEntity.getAccount()).eq(FollowTraderEntity::getPlatformId, followTraderUserEntity.getPlatformId()).orderByDesc(FollowTraderEntity::getType));
            if (ObjectUtil.isNotEmpty(followTraderEntity)){
                for (FollowTraderEntity fo : followTraderEntity) {
                    //检查vps是否正常
                    FollowVpsEntity followVpsEntity = followVpsService.getById(fo.getServerId());
                    if (followVpsEntity.getIsOpen().equals(CloseOrOpenEnum.CLOSE.getValue()) || followVpsEntity.getConnectionStatus().equals(CloseOrOpenEnum.CLOSE.getValue())) {
                        log.info(followVpsEntity.getName()+"VPS服务异常，请检查");
                    }else {
                        followTraderEntityList.add(fo);
                        break;
                    }
                }
            }
        });
        //创建父指令
        String orderNo = RandomStringUtil.generateNumeric(13);
        FollowOrderInstructEntity followOrderInstructEntity = FollowOrderInstructEntity.builder().instructionType(vo.getType())
                .maxLotSize(vo.getStartSize()).minLotSize(vo.getEndSize()).remark(vo.getRemark()).totalLots(vo.getTotalSzie())
                .totalOrders(vo.getTotalNum()).intervalTime(vo.getIntervalTime()).symbol(vo.getSymbol()).type(vo.getType())
                .orderNo(orderNo).creator(SecurityUser.getUserId()).createTime(LocalDateTime.now()).build();
        HttpHeaders headerApplicationJsonAndToken = RestUtil.getHeaderApplicationJsonAndToken(request);
        if (!followTraderEntityList.isEmpty()){
            if (vo.getTradeType().equals(FollowInstructEnum.DISTRIBUTION.getValue())){
                //交易分配，根据手数范围和总手数进行分配
                Map<FollowTraderEntity, Double> doubleMap = executeOrdersRandomTotalLots(followTraderEntityList, vo.getTotalSzie().doubleValue(), vo.getStartSize(), vo.getEndSize());
                followOrderInstructEntity.setTrueTotalOrders(doubleMap.size());
                followOrderInstructEntity.setTrueTotalLots(vo.getTotalSzie());
                followOrderInstructService.save(followOrderInstructEntity);
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                doubleMap.forEach((followTraderEntity, aDouble) -> {
                    CompletableFuture<Void> orderFuture = CompletableFuture.runAsync(() -> {
                        //发送请求
                        MasToSubOrderSendDto masToSubOrderSendDto = new MasToSubOrderSendDto();
                        masToSubOrderSendDto.setRemark(vo.getRemark());
                        masToSubOrderSendDto.setSymbol(vo.getSymbol());
                        masToSubOrderSendDto.setType(vo.getType());
                        masToSubOrderSendDto.setTraderId(followTraderEntity.getId());
                        masToSubOrderSendDto.setStartSize(vo.getStartSize());
                        masToSubOrderSendDto.setEndSize(vo.getEndSize());
                        masToSubOrderSendDto.setTotalNum(1);
                        masToSubOrderSendDto.setIntervalTime(0);
                        masToSubOrderSendDto.setTradeType(FollowInstructEnum.DISTRIBUTION.getValue());
                        masToSubOrderSendDto.setTotalSzie(BigDecimal.valueOf(aDouble));
                        masToSubOrderSendDto.setSendNo(orderNo);
                        sendRequest(request, followTraderEntity.getIpAddr(), HttpMethod.POST, FollowConstant.MASORDERSEND, masToSubOrderSendDto,headerApplicationJsonAndToken);
                    }, ThreadPoolUtils.getExecutor());
                    futures.add(orderFuture);
                });
                CompletableFuture<Void> allOrdersCompleted = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
                // 当所有订单任务完成后，执行更新操作
                allOrdersCompleted.thenRun(() -> {
                    log.info("所有分配交易下单已完成");
                });
            }else {
                AtomicInteger totalOrders = new AtomicInteger(0);
                AtomicReference<BigDecimal> totalLots = new AtomicReference<>(BigDecimal.ZERO);
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                followTraderEntityList.forEach(followTraderEntity -> {
                    CompletableFuture<Void> orderFuture = CompletableFuture.runAsync(() -> {
                        try {
                            // 发送请求
                            MasToSubOrderSendDto masToSubOrderSendDto = new MasToSubOrderSendDto();
                            masToSubOrderSendDto.setRemark(vo.getRemark());
                            masToSubOrderSendDto.setSymbol(vo.getSymbol());
                            masToSubOrderSendDto.setType(vo.getType());
                            masToSubOrderSendDto.setTraderId(followTraderEntity.getId());
                            masToSubOrderSendDto.setStartSize(vo.getStartSize());
                            masToSubOrderSendDto.setEndSize(vo.getEndSize());
                            masToSubOrderSendDto.setTotalNum(vo.getTotalNum());
                            masToSubOrderSendDto.setIntervalTime(vo.getIntervalTime());
                            masToSubOrderSendDto.setTradeType(FollowInstructEnum.COPY.getValue());
                            masToSubOrderSendDto.setTotalSzie(vo.getTotalSzie());
                            masToSubOrderSendDto.setTotalNum(vo.getTotalNum());
                            masToSubOrderSendDto.setSendNo(orderNo);

                            // 需等待发起请求
                            Result<?> result = sendRequest(request, followTraderEntity.getIpAddr(), HttpMethod.POST, FollowConstant.MASORDERSEND, masToSubOrderSendDto, headerApplicationJsonAndToken);
                            FollowMasOrderVo data = JSONObject.parseObject(result.getData().toString(),FollowMasOrderVo.class);
                            if (result.getCode() == 0) {
                                totalOrders.updateAndGet(v -> v + data.getTotalNum());
                                // 更新 totalLots，使用 AtomicReference 的 getAndUpdate 方法
                                totalLots.updateAndGet(current -> current.add(BigDecimal.valueOf(data.getTotalSize())));
                            }
                        } catch (Exception e) {
                            log.error("订单处理异常", e);
                            throw new RuntimeException(e); // 抛出异常让 allOf 感知
                        }
                    }, ThreadPoolUtils.getExecutor());
                    futures.add(orderFuture);
                });
                // 使用 CompletableFuture.allOf 等待所有订单任务完成
                CompletableFuture<Void> allOrdersCompleted = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
                // 当所有订单任务完成后，执行更新操作
                try {
                    allOrdersCompleted.get(30, TimeUnit.SECONDS);
                    log.info("所有复制交易下单已完成");
                    followOrderInstructEntity.setTrueTotalOrders(totalOrders.get());
                    followOrderInstructEntity.setTrueTotalLots(totalLots.get());
                    followOrderInstructService.save(followOrderInstructEntity);
                } catch (TimeoutException e) {
                    log.error("任务执行超时", e);
                } catch (InterruptedException | ExecutionException e) {
                    log.error("任务执行异常", e);
                }
            }
        }else {
            //无可以下单账号
            throw new ServerException("无可下单账号");
        }
    }
    //分配下单
    public static Map<FollowTraderEntity, Double> executeOrdersRandomTotalLots(List<FollowTraderEntity> traderId, double totalLots, BigDecimal minLots, BigDecimal maxLots) {
        Random rand = new Random();
        double totalPlacedLots = 0;
        Map<FollowTraderEntity, Double> accountOrders = new HashMap<>(); // 记录每个账号分配的手数
        // 遍历所有账号，随机分配手数
        for (FollowTraderEntity acc : traderId) {
            double remainingLotsForAccount = totalLots - totalPlacedLots;

            // 如果剩余的总手数为 0，则不再分配手数
            if (remainingLotsForAccount <= 0) {
                break;
            }

            // 随机生成当前账号的订单手数
            double randomLots = roundToTwoDecimal(minLots.doubleValue() +
                    (maxLots.doubleValue() - minLots.doubleValue()) * rand.nextDouble());

            // 限制手数不超过剩余总手数
            if (totalPlacedLots + randomLots > totalLots) {
                randomLots = remainingLotsForAccount;
            }

            // 将当前账号的订单手数加到总手数
            accountOrders.put(acc, randomLots);
            totalPlacedLots += randomLots;

            // 如果总手数已分配完，则跳出循环
            if (totalPlacedLots >= totalLots) {
                break;
            }
        }

        // 如果分配后总手数仍然未达到 totalLots，尝试分配剩余的差值
        double remainingDiff = totalLots - totalPlacedLots;
        if (remainingDiff > 0) {
            // 查找手数最少的账号，补充剩余手数
            FollowTraderEntity minAccount = null;
            double minAccountOrder = Double.MAX_VALUE;
            for (Map.Entry<FollowTraderEntity, Double> entry : accountOrders.entrySet()) {
                if (entry.getValue() < minAccountOrder) {
                    minAccountOrder = entry.getValue();
                    minAccount = entry.getKey();
                }
            }

            // 将剩余手数添加到手数最少的账号
            if (minAccount != null) {
                accountOrders.put(minAccount, roundToTwoDecimal(minAccountOrder + remainingDiff));
                totalPlacedLots += remainingDiff;
            }
        }

        // 过滤未分配手数的账号
        accountOrders = accountOrders.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        // 如果总订单手数为 0，抛出异常
        if (accountOrders.isEmpty()) {
            throw new ServerException("请重新下单");
        }

        log.info("执行随机下单操作，总手数不超过 " + totalLots + "，实际分配订单数: " + accountOrders.size());
        log.info("下单数量{}", accountOrders);

        return accountOrders;
    }

    // 保留两位小数的方法
    public static double roundToTwoDecimal(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
