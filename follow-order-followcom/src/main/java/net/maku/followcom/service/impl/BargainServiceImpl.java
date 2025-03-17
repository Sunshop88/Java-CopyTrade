package net.maku.followcom.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.dto.MasOrderSendDto;
import net.maku.followcom.dto.MasToSubOrderCloseDto;
import net.maku.followcom.entity.*;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.enums.FollowInstructEnum;
import net.maku.followcom.enums.FollowMasOrderStatusEnum;
import net.maku.followcom.enums.TradeErrorCodeEnum;
import net.maku.followcom.service.*;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.util.RestUtil;
import net.maku.followcom.vo.FollowMasOrderVo;
import net.maku.followcom.vo.FollowOrderSendCloseVO;
import net.maku.framework.common.cache.RedisCache;
import net.maku.framework.common.cache.RedissonLockUtil;
import net.maku.framework.common.constant.Constant;
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
import java.math.RoundingMode;
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
    private final RedissonLockUtil redissonLockUtil;
    private final FollowOrderDetailService followOrderDetailService;
    private final FollowPlatformService followPlatformService;
    private final RedisCache redisCache;
    @Override
    public void masOrderSend(MasOrderSendDto vo, HttpServletRequest request) {
        if (vo.getStartSize().compareTo(vo.getEndSize())>0) {
            throw new ServerException("开始手数不能大于结束手数");
        }
        if (vo.getStartSize().compareTo(vo.getTotalSzie())>0) {
            throw new ServerException("总手数不能低于最低手数");
        }
        //现在找出可下单账号
        List<FollowTraderEntity> followTraderEntityList=new ArrayList<>();
        vo.getTraderList().forEach(o->{
            FollowTraderUserEntity followTraderUserEntity = followTraderUserService.getById(o);
            if (ObjectUtil.isEmpty(followTraderUserEntity)){
                throw new ServerException("请求异常");
            }
            //查询各VPS状态正常的账号 -优先状态正常 -优先交易分配
            List<FollowTraderEntity> followTraderEntity = followTraderService.list(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getAccount, followTraderUserEntity.getAccount()).eq(FollowTraderEntity::getPlatformId, followTraderUserEntity.getPlatformId()).orderByAsc(FollowTraderEntity::getStatus).orderByDesc(FollowTraderEntity::getType));
            if (ObjectUtil.isNotEmpty(followTraderEntity)){
                for (FollowTraderEntity fo : followTraderEntity) {
                    //检查vps是否正常
                    FollowVpsEntity followVpsEntity = followVpsService.getById(fo.getServerId());
                    if (followVpsEntity.getIsOpen().equals(CloseOrOpenEnum.CLOSE.getValue()) || followVpsEntity.getConnectionStatus().equals(CloseOrOpenEnum.CLOSE.getValue())|| followVpsEntity.getIsActive().equals(CloseOrOpenEnum.CLOSE.getValue())) {
                        log.info(followVpsEntity.getName()+"VPS服务异常，请检查");
                    }else {
                        followTraderEntityList.add(fo);
                        break;
                    }
                }
            }else {
                //没有挂靠账号
                log.info("账号ID:"+o+"没有挂靠VPS");
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
                //分配账号总数
                //交易分配，根据手数范围和总手数进行分配
                Map<FollowTraderEntity, Double> doubleMap = executeOrdersRandomTotalLots(followTraderEntityList, vo.getTotalSzie(), vo.getStartSize(), vo.getEndSize());
                //插入第一个用户的id
                followOrderInstructEntity.setTraderId(followTraderEntityList.get(0).getId().intValue());
                followOrderInstructEntity.setTrueTotalOrders(doubleMap.size());
                followOrderInstructEntity.setTrueTotalLots(vo.getTotalSzie());
                followOrderInstructService.save(followOrderInstructEntity);
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                doubleMap.forEach((followTraderEntity, aDouble) -> {
                    CompletableFuture<Void> orderFuture = CompletableFuture.runAsync(() -> {
                        try {
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
                            Result result = sendRequest(request, followTraderEntity.getIpAddr(), HttpMethod.POST, FollowConstant.MASORDERSEND, masToSubOrderSendDto, headerApplicationJsonAndToken);
                            if (result.getCode() != 0) {
                                //增加子指令数据
                                insertOrderDetail(followTraderEntity, vo, orderNo, aDouble,1,result.getMsg());
                                log.info("分配交易下单请求异常"+followTraderEntity.getId());
                            }
                        }catch (Exception e) {
                            log.error("订单处理异常", e);
                            insertOrderDetail(followTraderEntity,vo,orderNo,aDouble,1,"服务器异常");
                        }
                    }, ThreadPoolUtils.getExecutor());
                    futures.add(orderFuture);
                });
                CompletableFuture<Void> allOrdersCompleted = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
                // 当所有订单任务完成后，执行更新操作
                allOrdersCompleted.thenRun(() -> {
                    log.info("所有分配交易下单已完成");
                    try {
                        allOrdersCompleted.get(30, TimeUnit.SECONDS);
                        updateInstruct(orderNo);
                    } catch (InterruptedException | ExecutionException | TimeoutException e) {
                        throw new RuntimeException(e);
                    }
                });
            }else {
                followOrderInstructEntity.setTraderId(followTraderEntityList.get(0).getId().intValue());
                followOrderInstructService.save(followOrderInstructEntity);
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
                            }else {
                                //增加子指令
                                log.info("复制交易下单请求异常"+followTraderEntity.getId());
                                insertOrderDetail(followTraderEntity,vo,orderNo,vo.getStartSize().doubleValue(),vo.getTotalNum(),result.getMsg());
                            }
                        } catch (Exception e) {
                            log.error("订单处理异常", e);
                            insertOrderDetail(followTraderEntity,vo,orderNo,vo.getStartSize().doubleValue(),vo.getTotalNum(),"服务器异常");
                        }
                    }, ThreadPoolUtils.getExecutor());
                    futures.add(orderFuture);
                });
                // 使用 CompletableFuture.allOf 等待所有订单任务完成
                CompletableFuture<Void> allOrdersCompleted = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
                // 当所有订单任务完成后，执行更新操作
                allOrdersCompleted.thenRun(() -> {
                    log.info("所有复制交易下单下单已完成");
                    try {
                        allOrdersCompleted.get(30, TimeUnit.SECONDS);
                        followOrderInstructEntity.setTrueTotalOrders(totalOrders.get());
                        followOrderInstructEntity.setTrueTotalLots(totalLots.get());
                        followOrderInstructService.updateById(followOrderInstructEntity);
                    } catch (InterruptedException | ExecutionException | TimeoutException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        }else {
            //无可以下单账号
            throw new ServerException("无可下单账号");
        }
    }

    private void updateInstruct(String orderNo) {
        if (redissonLockUtil.tryLockForShortTime("masOrder" + orderNo, 100, 120, TimeUnit.SECONDS)) {
            try {
                //增加成交订单数和手数
                FollowOrderInstructEntity followOrderInstruct = followOrderInstructService.getOne(new LambdaQueryWrapper<FollowOrderInstructEntity>().eq(FollowOrderInstructEntity::getOrderNo, orderNo));
                List<FollowOrderDetailEntity> list=followOrderDetailService.list(new LambdaQueryWrapper<FollowOrderDetailEntity>().eq(FollowOrderDetailEntity::getSendNo, orderNo));
                followOrderInstruct.setTradedOrders((int) list.stream().filter(o -> ObjectUtil.isNotEmpty(o.getOpenTime())).count());
                followOrderInstruct.setTradedLots((list.stream().filter(o -> ObjectUtil.isNotEmpty(o.getOpenTime())).map(FollowOrderDetailEntity::getSize).reduce(BigDecimal.ZERO, BigDecimal::add)));
                followOrderInstruct.setFailOrders((int) list.stream().filter(o -> ObjectUtil.isNotEmpty(o.getRemark())).count());
                if (followOrderInstruct.getTradedOrders()==followOrderInstruct.getTrueTotalOrders()){
                    log.info("交易下单已完成-全部成功");
                    followOrderInstruct.setEndTime(LocalDateTime.now());
                    followOrderInstruct.setStatus(FollowMasOrderStatusEnum.ALLSUCCESS.getValue());
                }else if (followOrderInstruct.getFailOrders()+followOrderInstruct.getTradedOrders()==followOrderInstruct.getTrueTotalOrders()){
                    log.info("交易下单已完成-部分成功");
                    followOrderInstruct.setEndTime(LocalDateTime.now());
                    followOrderInstruct.setStatus(FollowMasOrderStatusEnum.PARTIALFAILURE.getValue());
                }
                followOrderInstructService.updateById(followOrderInstruct);
            }catch (Exception e){
                log.info("修改总指令异常"+e.getMessage());
            }finally {
                if (redissonLockUtil.isLockedByCurrentThread("masOrder" + orderNo)) {
                    redissonLockUtil.unlock("masOrder" + orderNo);
                }
            }
        }
    }

    private void insertOrderDetail(FollowTraderEntity followTraderEntity, MasOrderSendDto vo, String orderNo, Double aDouble,Integer num,String msg) {
        List<FollowOrderDetailEntity> followOrderDetailEntities=new ArrayList<>();
        FollowPlatformEntity platFormById = followPlatformService.getPlatFormById(followTraderEntity.getPlatformId().toString());
        for (int i=0;i<num;i++){
            FollowOrderDetailEntity followOrderDetailEntity=new FollowOrderDetailEntity();
            followOrderDetailEntity.setSendNo(orderNo);
            followOrderDetailEntity.setTraderId(followTraderEntity.getId());
            followOrderDetailEntity.setAccount(followTraderEntity.getAccount());
            followOrderDetailEntity.setSize(new BigDecimal(aDouble));
            followOrderDetailEntity.setSymbol(vo.getSymbol());
            followOrderDetailEntity.setRemark(msg);
            followOrderDetailEntity.setType(vo.getType());
            followOrderDetailEntity.setBrokeName(platFormById.getBrokerName());
            followOrderDetailEntity.setServerName(followTraderEntity.getServerName());
            followOrderDetailEntity.setIpAddr(followTraderEntity.getIpAddr());
            followOrderDetailEntity.setPlatform(platFormById.getServer());
            followOrderDetailEntities.add(followOrderDetailEntity);
        }
        followOrderDetailService.saveBatch(followOrderDetailEntities);
    }

    @Override
    public void masOrderClose(MasToSubOrderCloseDto vo, HttpServletRequest request) {
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
        if (ObjectUtil.isEmpty(followTraderEntityList)){
            throw new ServerException("无可下单账号");
        }
        HttpHeaders headerApplicationJsonAndToken = RestUtil.getHeaderApplicationJsonAndToken(request);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        followTraderEntityList.forEach(followTraderEntity -> {
            CompletableFuture<Void> orderFuture = CompletableFuture.runAsync(() -> {
                try {
                    // 发送请求
                    FollowOrderSendCloseVO followOrderSendCloseVO = new FollowOrderSendCloseVO();
                    BeanUtil.copyProperties(vo,followOrderSendCloseVO);
                    followOrderSendCloseVO.setTraderId(followTraderEntity.getId());
                    // 需等待发起请求
                    sendRequest(request, followTraderEntity.getIpAddr(), HttpMethod.POST, FollowConstant.MASORDERCLOSE, followOrderSendCloseVO, headerApplicationJsonAndToken);
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
            log.info("所有交易平仓已完成");
        } catch (TimeoutException e) {
            log.error("任务执行超时", e);
        } catch (InterruptedException | ExecutionException e) {
            log.error("任务执行异常", e);
        }
    }

    @Override
    public void stopOrder(Integer type, String orderNo) {
        //指令停止
        if (type == 0) {
            FollowOrderInstructEntity orderInstructServiceOne = followOrderInstructService.getOne(new LambdaQueryWrapper<FollowOrderInstructEntity>().eq(FollowOrderInstructEntity::getOrderNo, orderNo));
            if (ObjectUtil.isNotEmpty(orderInstructServiceOne) && orderInstructServiceOne.getIntervalTime() != 0) {
                //只会停止间隔下单
                //停止下单
                if (ObjectUtil.isNotEmpty(redisCache.get(Constant.TRADER_SEND_MAS + orderNo))) {
                    redisCache.set(Constant.TRADER_SEND_MAS + orderNo, 2);
                }
                orderInstructServiceOne.setEndTime(LocalDateTime.now());
                orderInstructServiceOne.setStatus(FollowMasOrderStatusEnum.PARTIALFAILURE.getValue());
                followOrderInstructService.updateById(orderInstructServiceOne);
            }else {
                throw new ServerException("不存在正在执行订单");
            }
        } else {
            if (ObjectUtil.isNotEmpty(redisCache.get(Constant.TRADER_CLOSE_MAS + orderNo))) {
                redisCache.set(Constant.TRADER_CLOSE_MAS + orderNo, 2);
            }else {
                throw new ServerException("不存在正在平仓订单");
            }
        }
    }

    //分配下单
     public static Map<FollowTraderEntity, Double> executeOrdersRandomTotalLots(List<FollowTraderEntity> traderId, BigDecimal totalLots, BigDecimal minLots, BigDecimal maxLots) {
        Random rand = new Random();
        Map<FollowTraderEntity, Double> accountOrders = new HashMap<>(); // 记录每个账号分配的手数
        BigDecimal totalPlacedLots = BigDecimal.ZERO;  // 已下单的总手数
        int orderCountNum = 0;                         // 已下单的订单数量
        List<BigDecimal> orders = new ArrayList<>();
        int orderCount = traderId.size();

        while (totalPlacedLots.compareTo(totalLots) < 0 && orderCountNum < orderCount) {
            // 生成随机手数，并四舍五入保留两位小数
            BigDecimal randomLots = minLots.add(maxLots.subtract(minLots).multiply(new BigDecimal(rand.nextDouble())))
                    .setScale(2, RoundingMode.HALF_UP);

            // 检查生成的随机手数是否超过剩余手数
            if (totalPlacedLots.add(randomLots).compareTo(totalLots) > 0) {
                randomLots = totalLots.subtract(totalPlacedLots).setScale(2, RoundingMode.HALF_UP);
            }

            // 防止生成的订单手数为 0
            if (randomLots.compareTo(BigDecimal.ZERO) > 0) {
                orders.add(randomLots);
                totalPlacedLots = totalPlacedLots.add(randomLots);
                orderCountNum++;
            }
        }

        // 如果还有剩余手数，按比例分配给每个订单
        BigDecimal remainingLots = totalLots.subtract(totalPlacedLots).setScale(2, RoundingMode.HALF_UP);
        if (remainingLots.compareTo(BigDecimal.ZERO) > 0 && !orders.isEmpty()) {
            BigDecimal lotsToAddPerOrder = remainingLots.divide(new BigDecimal(orderCountNum), 2, RoundingMode.DOWN);

            BigDecimal cumulativeRemainder = remainingLots.subtract(lotsToAddPerOrder.multiply(new BigDecimal(orderCountNum)));

            for (int i = 0; i < orders.size(); i++) {
                BigDecimal updatedOrder = orders.get(i).add(lotsToAddPerOrder).setScale(2, RoundingMode.HALF_UP);
                if (cumulativeRemainder.compareTo(BigDecimal.ZERO) > 0) {
                    updatedOrder = updatedOrder.add(new BigDecimal("0.01"));
                    cumulativeRemainder = cumulativeRemainder.subtract(new BigDecimal("0.01"));
                }
                orders.set(i, updatedOrder);
            }
        }

        // 最终确认总手数并调整误差，确保不超过 totalLots
        BigDecimal finalTotal = orders.stream().reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        if (finalTotal.compareTo(totalLots) > 0) {
            BigDecimal excess = finalTotal.subtract(totalLots);
            for (int i = 0; i < orders.size() && excess.compareTo(BigDecimal.ZERO) > 0; i++) {
                BigDecimal order = orders.get(i);
                BigDecimal adjustment = order.min(excess).setScale(2, RoundingMode.HALF_UP);
                orders.set(i, order.subtract(adjustment));
                excess = excess.subtract(adjustment);
            }
        } else if (finalTotal.compareTo(totalLots) < 0) {
            BigDecimal deficit = totalLots.subtract(finalTotal);
            int randomOrderIndex = rand.nextInt(orders.size());
            orders.set(randomOrderIndex, orders.get(randomOrderIndex).add(deficit).setScale(2, RoundingMode.HALF_UP));
        }

        // 过滤 0 值
        orders = orders.stream().filter(o -> o.compareTo(BigDecimal.ZERO) > 0).collect(Collectors.toList());

        if (orders.isEmpty()) {
            // 下单异常，抛出异常
            throw new ServerException("请重新下单");
        }
        log.info("下单数量{}++++++++下单手数{}", orders.size(), orders);

        // 将手数分配给每个 traderId
        for (int i = 0; i < orders.size(); i++) {
            accountOrders.put(traderId.get(i), orders.get(i).doubleValue());
        }
        log.info("执行有限订单数量随机下单操作，总手数不超过" + totalLots + "，最大订单数: " + orderCount + "，实际下单订单数: " + accountOrders.size());

        return accountOrders;
    }

}
