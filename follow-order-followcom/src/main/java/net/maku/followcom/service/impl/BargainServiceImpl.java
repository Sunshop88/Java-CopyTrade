package net.maku.followcom.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.dto.MasOrderSendDto;
import net.maku.followcom.entity.FollowOrderInstructEntity;
import net.maku.followcom.entity.FollowOrderInstructSubEntity;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.entity.FollowTraderUserEntity;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.enums.FollowInstructEnum;
import net.maku.followcom.enums.TraderTypeEnum;
import net.maku.followcom.service.*;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.util.RestUtil;
import net.maku.followcom.vo.FollowOrderSendVO;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.exception.ServerException;
import net.maku.framework.common.utils.Result;
import net.maku.framework.security.user.SecurityUser;
import online.mtapi.mt4.QuoteClient;
import org.jacoco.agent.rt.internal_1f1cc91.core.internal.flow.IFrame;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import net.maku.followcom.dto.MasToSubOrderSendDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

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

        //现在找出可下单账号
        List<FollowTraderEntity> followTraderEntityList=new ArrayList<>();
        vo.getTraderList().forEach(o->{
            FollowTraderUserEntity followTraderUserEntity = followTraderUserService.getById(o);
            //查询各VPS状态正常的账号
            Optional<FollowTraderEntity> followTraderEntity = followTraderService.list(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getStatus, CloseOrOpenEnum.CLOSE.getValue()).eq(FollowTraderEntity::getAccount, followTraderUserEntity.getAccount()).eq(FollowTraderEntity::getPlatformId, followTraderUserEntity.getPlatformId())).stream().findFirst();
            followTraderEntity.ifPresent(followTraderEntityList::add);
        });

        if (!followTraderEntityList.isEmpty()){
            //创建父指令
            FollowOrderInstructEntity followOrderInstructEntity = FollowOrderInstructEntity.builder().instructionType(vo.getType())
                    .maxLotSize(vo.getStartSize()).minLotSize(vo.getEndSize()).remark(vo.getRemark()).totalLots(vo.getTotalSzie())
                    .totalOrders(vo.getTotalNum()).intervalTime(vo.getIntervalTime()).symbol(vo.getSymbol()).type(vo.getType())
                    .creator(SecurityUser.getUserId()).createTime(LocalDateTime.now()).build();

            if (vo.getTradeType().equals(FollowInstructEnum.DISTRIBUTION.getValue())){
                //交易分配，根据手数范围和总手数进行分配
                Map<FollowTraderEntity, Double> doubleMap = executeOrdersRandomTotalLots(followTraderEntityList, vo.getTotalSzie().doubleValue(), vo.getStartSize(), vo.getEndSize());
                //总订单
                followOrderInstructEntity.setTotalOrders(doubleMap.size());
                followOrderInstructService.save(followOrderInstructEntity);
                doubleMap.forEach((followTraderEntity, aDouble) -> {
                    //创建子指令
                    FollowOrderInstructSubEntity followOrderInstructSubEntity = FollowOrderInstructSubEntity.builder().instructId(followOrderInstructEntity.getId())
                            .account(followTraderEntity.getAccount()).accountType("MT4").symbol(vo.getSymbol()).type(vo.getType()).lots(BigDecimal.valueOf(aDouble))
                            .platform(followTraderEntity.getPlatform()).createTime(LocalDateTime.now()).creator(SecurityUser.getUserId()).build();
                    followOrderInstructSubService.save(followOrderInstructSubEntity);

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
                    RestUtil.sendRequest(request, followTraderEntity.getIpAddr(), HttpMethod.POST, FollowConstant.MASORDERSEND, masToSubOrderSendDto,null);
                });
            }else {
                //复制 所有账号相同参数
                followOrderInstructEntity.setTotalOrders(followTraderEntityList.size());
                followOrderInstructService.save(followOrderInstructEntity);

                followTraderEntityList.forEach(followTraderEntity -> {
                    //获取随机值
                    //创建子指令
                    FollowOrderInstructSubEntity followOrderInstructSubEntity = FollowOrderInstructSubEntity.builder().instructId(followOrderInstructEntity.getId())
                            .account(followTraderEntity.getAccount()).accountType("MT4").symbol(vo.getSymbol()).type(vo.getType()).lots(BigDecimal.valueOf(aDouble))
                            .platform(followTraderEntity.getPlatform()).createTime(LocalDateTime.now()).creator(SecurityUser.getUserId()).build();
                    followOrderInstructSubService.save(followOrderInstructSubEntity);

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
                    RestUtil.sendRequest(request, followTraderEntity.getIpAddr(), HttpMethod.POST, FollowConstant.MASORDERSEND, masToSubOrderSendDto, null);
                });
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
        List<Double> orders = new ArrayList<>();
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

    //  每笔订单的下单数量为 区间内的随机值，总手数和订单数量都受限制
    public void executeOrdersRandomLimited( long traderId, String account, Integer type, QuoteClient quoteClient, String symbol, Integer orderCount, BigDecimal totalLots, BigDecimal minLots, BigDecimal maxLots, Integer interval, String orderNo,String remark) {
        Random rand = new Random();
        BigDecimal totalPlacedLots = BigDecimal.ZERO;  // 已下单的总手数
        int orderCountNum = 0;                         // 已下单的订单数量
        List<BigDecimal> orders = new ArrayList<>();
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

        // 最终确认总手数并调整误差
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
        log.info("执行有限订单数量随机下单操作，总手数不超过" + totalLots + "，最大订单数: " + orderCount + "，实际下单订单数: " + orders.size());

        if (orders.isEmpty()) {
            // 下单异常，抛出异常
            throw new ServerException("请重新下单");
        }
        // 保存实际下单的订单数量
        vo.setTotalNum(orders.size());
        followOrderSendService.save(vo);
        redisCache.delete(Constant.TRADER_ORDER + traderId);
        if (pr.compareTo(BigDecimal.ONE) != 0) {
            // 使用索引更新列表中的值
            for (int i = 0; i < orders.size(); i++) {
                BigDecimal orderValue = orders.get(i);
                BigDecimal newOrderValue = orderValue.multiply(pr);

                if (newOrderValue.compareTo(new BigDecimal("0.01")) < 0) {
                    // 如果小于 0.01，则设置为 0.01
                    orders.set(i, new BigDecimal("0.01"));
                } else {
                    // 否则将新的值设置回 orders 列表
                    orders.set(i, newOrderValue);
                }
            }
        }
        log.info("下单数量{}", orders);
        // 执行订单操作
        List<Double> collect = orders.stream().map(BigDecimal::doubleValue).collect(Collectors.toList());
        if (followVpsService.getVps(FollowConstant.LOCAL_HOST).getIsSyn().equals(CloseOrOpenEnum.OPEN.getValue())){
            executeOrder(ipAdd, serverName, platform, brokerName, interval, orderCountNum, collect, traderId, account, quoteClient, symbol, type, orderNo, vo.getPlacedType(),remark);
        }else {
            executeOrderNoSyn(ipAdd, serverName, platform, brokerName, interval, orderCountNum, collect, traderId, account, quoteClient, symbol, type, orderNo, vo.getPlacedType(),remark);
        }
    }
}
