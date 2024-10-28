package net.maku.subcontrol.task;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cld.message.pubsub.kafka.CldProducerRecord;
import com.cld.message.pubsub.kafka.IKafkaProducer;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.*;
import net.maku.followcom.enums.AcEnum;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.service.FollowOrderHistoryService;
import net.maku.followcom.service.FollowSubscribeOrderService;
import net.maku.followcom.service.FollowTraderService;
import net.maku.followcom.service.FollowTraderSubscribeService;
import net.maku.followcom.service.impl.FollowSubscribeOrderServiceImpl;
import net.maku.followcom.service.impl.FollowTraderServiceImpl;
import net.maku.followcom.service.impl.FollowTraderSubscribeServiceImpl;
import net.maku.followcom.util.FollowConstant;
import net.maku.subcontrol.constants.KafkaTopicPrefixSuffix;
import net.maku.subcontrol.util.KafkaTopicUtil;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.subcontrol.trader.ApiTrader;
import net.maku.subcontrol.trader.CopierApiTrader;
import net.maku.subcontrol.trader.LeaderApiTrader;
import online.mtapi.mt4.Order;

import java.util.Arrays;
import java.util.regex.Pattern;

import static java.lang.Thread.sleep;

/**
 * 循环平仓任务
 *
 * @author samson bruce
 */
@Data
@Slf4j
public class CycleCloseOrderTask implements Runnable {
    FollowTraderEntity copier;
    CopierApiTrader copierApiTrader;
    LeaderApiTrader leaderApiTrader;
    FollowTraderService traderService;
    FollowTraderSubscribeService leaderCopierService;
    FollowSubscribeOrderService openOrderMappingService;
    FollowOrderHistoryService followOrderHistoryService;
    IKafkaProducer<String, Object> kafkaProducer;
    Boolean running = Boolean.TRUE;
    int calledTimes = 0;
    int intervals = 10;

    public CycleCloseOrderTask(CopierApiTrader copierApiTrader, IKafkaProducer<String, Object> kafkaProducer) {
        this.copierApiTrader = copierApiTrader;
        this.copier = copierApiTrader.getTrader();
        this.traderService = SpringContextUtils.getBean(FollowTraderServiceImpl.class);
        this.leaderCopierService = SpringContextUtils.getBean(FollowTraderSubscribeServiceImpl.class);
        this.openOrderMappingService = SpringContextUtils.getBean(FollowSubscribeOrderServiceImpl.class);
        this.kafkaProducer = kafkaProducer;
        this.followOrderHistoryService = SpringContextUtils.getBean(FollowOrderHistoryService.class);
    }

    @Override
    public void run() {
        try {
            while (running) {
                Order[] orders = copierApiTrader.quoteClient.GetOpenedOrders();
                calledTimes = calledTimes + orders.length;
                Arrays.stream(orders).filter(this::systemOrderSend).forEach(this::send2Leader);
                try {
                    //10秒检测一次 循环平仓
                    sleep(intervals * 1000L);
//                    FollowTraderEntity traderFromDb = traderService.getById(copier.getId());
//                    Integer isProtectEquity = traderFromDb.getIsProtectEquity();
//                    if (isProtectEquity == 1) {
//                        copierApiTrader.riskControl(traderFromDb);
//                    }
                    if (calledTimes * intervals > 120) {
                        calledTimes = 0;
                        //更新前后缀和账号信息
                        copierApiTrader.updateTraderInfo();
                    }
                } catch (Exception e) {
                    log.error("", e);
                }
            }
        } catch (Exception e) {
            log.error("", e);
        }
    }

    boolean systemOrderSend(Order order) {
        return null != order.Comment && Pattern.matches(ApiTrader.regex, order.Comment);
    }

    /**
     * 循环
     *
     * @param order 跟单者持仓订单
     */
    void send2Leader(Order order) {
//        FollowTraderEntity traderFromDb = traderService.getById(copier.getId());
//        try {
//            if (traderFromDb != null) {
//                Integer isProtectEquity = traderFromDb.getIsProtectEquity();
//                if (isProtectEquity == 1) {
//                    copierApiTrader.riskControl(traderFromDb);
//                }
//            }
//        } catch (Exception e) {
//            log.error("账号：{}-{}-{}风控报错", traderFromDb.getId(), traderFromDb.getServerName(), traderFromDb.getAccount(), e);
//        }
        if (!running) {
            return;
        }
        try {
            int indexOf = order.Comment.indexOf("#", 1);
            String leaderAccount = String.valueOf(Long.parseLong(order.Comment.substring(1, indexOf), 36));
            long leaderTicket;

            //获取当前vps信号源的账号
            FollowTraderEntity leader = traderService.getOne(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getIpAddr, FollowConstant.LOCAL_HOST).eq(FollowTraderEntity::getAccount,leaderAccount));

            if (ObjectUtil.isNotEmpty(leader)) {
                leaderTicket = Long.parseLong(order.Comment.split("#")[2], 36);
                //查看信号源是否存在该历史订单
                FollowOrderHistoryEntity followOrderHistory = followOrderHistoryService.getOne(new LambdaQueryWrapper<FollowOrderHistoryEntity>().eq(FollowOrderHistoryEntity::getOrderNo,leaderTicket).eq(FollowOrderHistoryEntity::getTraderId,leader.getId()));
                if (ObjectUtil.isNotEmpty(followOrderHistory)) {
                    FollowTraderSubscribeEntity leaderCopier = leaderCopierService.getOne(new LambdaQueryWrapper<FollowTraderSubscribeEntity>().eq(FollowTraderSubscribeEntity::getMasterId,leader.getId()).eq(FollowTraderSubscribeEntity::getSlaveId,copier.getId()));
                    if (leaderCopier == null) {
                        openOrderMappingService.update(Wrappers.<FollowSubscribeOrderEntity>lambdaUpdate().set(FollowSubscribeOrderEntity::getExtra, "订阅关系不存在,不循环平仓").eq(FollowSubscribeOrderEntity::getMasterId, leader.getId()).eq(FollowSubscribeOrderEntity::getSlaveId, copier.getId()).eq(FollowSubscribeOrderEntity::getSlaveTicket, order.Ticket));
                        return;
                    }

                    if (!leaderCopier.getPause().equals(CloseOrOpenEnum.OPEN.getValue())) {
                        openOrderMappingService.update(Wrappers.<FollowSubscribeOrderEntity>lambdaUpdate().set(FollowSubscribeOrderEntity::getExtra, "订阅状态不正常,不循环平仓").eq(FollowSubscribeOrderEntity::getMasterId, leader.getId()).eq(FollowSubscribeOrderEntity::getSlaveId, copier.getId()).eq(FollowSubscribeOrderEntity::getSlaveTicket, order.Ticket));
                        return;
                    }
                    if (leaderCopier.getCreateTime().isAfter(order.OpenTime)) {
                        openOrderMappingService.update(Wrappers.<FollowSubscribeOrderEntity>lambdaUpdate().set(FollowSubscribeOrderEntity::getExtra, "开仓时间在订阅开始前,不循环平仓").eq(FollowSubscribeOrderEntity::getMasterId, leader.getId()).eq(FollowSubscribeOrderEntity::getSlaveId, copier.getId()).eq(FollowSubscribeOrderEntity::getSlaveTicket, order.Ticket));
                        return;
                    }
                    long count = Arrays.stream(copierApiTrader.quoteClient.GetOpenedOrders()).filter(o -> o.Ticket == order.Ticket).count();
                    if (count > 0) {
                        FollowOrderActiveEntity copierActiveOrder = new FollowOrderActiveEntity(order, copier.getId());
                        // 向跟单者发送平仓失败的订单
                        CldProducerRecord<String, Object> cldProducerRecord = new CldProducerRecord<>(KafkaTopicPrefixSuffix.TENANT, KafkaTopicUtil.copierAccountTopic(String.valueOf(copierActiveOrder.getTraderId())), AcEnum.FC.getKey(), copierActiveOrder);
                        this.kafkaProducer.send(cldProducerRecord);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                sleep(intervals * 1000L);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
