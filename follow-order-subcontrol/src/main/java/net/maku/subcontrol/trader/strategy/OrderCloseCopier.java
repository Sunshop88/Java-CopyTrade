package net.maku.subcontrol.trader.strategy;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowSubscribeOrderEntity;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.entity.FollowTraderSubscribeEntity;
import net.maku.followcom.enums.AcEnum;
import net.maku.followcom.enums.CopyTradeFlag;
import net.maku.followcom.pojo.EaOrderInfo;
import net.maku.followcom.service.FollowTraderService;
import net.maku.subcontrol.pojo.CachedCopierOrderInfo;
import net.maku.subcontrol.service.IOperationStrategy;
import net.maku.subcontrol.trader.AbstractOperation;
import net.maku.subcontrol.trader.CopierApiTrader;
import online.mtapi.mt4.Order;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.util.ObjectUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static net.maku.followcom.enums.CopyTradeFlag.CPOS;
import static net.maku.followcom.enums.CopyTradeFlag.POF;


/**
 * MT跟单对象收到信号后，平仓操作
 */
@Slf4j
public class OrderCloseCopier extends AbstractOperation implements IOperationStrategy {
    FollowTraderEntity copier;
    CopierApiTrader copierApiTrader;
    FollowTraderService aotfxTraderService;

    public OrderCloseCopier(CopierApiTrader copierApiTrader) {
        super(copierApiTrader.getTrader());
        this.copierApiTrader = copierApiTrader;
        this.copier = this.copierApiTrader.getTrader();
    }

    @Override
    public void operate(ConsumerRecord<String, Object> record, int retry) {
        FollowTraderSubscribeEntity leaderCopier;
        Long orderId = copier.getId();
        //处理循环平仓
        if (record.key().equalsIgnoreCase(AcEnum.FC.getKey())) {
//            AotfxActiveOrder activeOrder = (AotfxActiveOrder) record.value();
//            leaderCopier = leaderCopierService.subscription(String.valueOf(orderId), activeOrder.getMasterId().toString());
//            closeCycle(leaderCopier, activeOrder);
        } else {
            //处理即时跟平
            EaOrderInfo orderInfo = (EaOrderInfo) record.value();
            orderInfo.setSlaveReceiveCloseTime(orderInfo.getSlaveReceiveCloseTime() == null ? LocalDateTime.now() : orderInfo.getSlaveReceiveCloseTime());
            // 通过map获取平仓订单号
            log.debug("[MT4跟单者:{}-{}-{}]开始尝试平仓，[喊单者:{}-{}-{}],喊单者订单信息[{}]", orderId, copier.getAccount(), copier.getServerName(), orderInfo.getMasterId(), orderInfo.getAccount(), orderInfo.getServer(), orderInfo);
            // 跟单者(和当前喊单者的平仓订单)，对应的订单号。
            CachedCopierOrderInfo cachedCopierOrderInfo = (CachedCopierOrderInfo) redisUtil.hGet(mapKey, Long.toString(orderInfo.getTicket()));
            if (ObjectUtils.isEmpty(cachedCopierOrderInfo)) {
                try {
                    FollowSubscribeOrderEntity openOrderMapping = openOrderMappingService.getOne(Wrappers.<FollowSubscribeOrderEntity>lambdaQuery()
                            .eq(FollowSubscribeOrderEntity::getMasterId, orderInfo.getMasterId())
                            .eq(FollowSubscribeOrderEntity::getSlaveId, orderId)
                            .eq(FollowSubscribeOrderEntity::getMasterTicket, orderInfo.getTicket()));
                    if (!ObjectUtils.isEmpty(openOrderMapping)) {
                        cachedCopierOrderInfo = new CachedCopierOrderInfo(openOrderMapping);
                    } else {
                        cachedCopierOrderInfo = new CachedCopierOrderInfo();
                    }
                } catch (Exception ignored) {
                }
            }
            if (ObjectUtils.isEmpty(cachedCopierOrderInfo.getSlaveTicket())) {
                log.error("[MT4跟单者:{}-{}-{}]没有找到对应平仓订单号,因为该对应的订单开仓失败，[喊单者:{}-{}-{}],喊单者订单信息[{}]", orderId, copier.getAccount(), copier.getServerName(), orderInfo.getMasterId(), orderInfo.getAccount(), orderInfo.getServer(), orderInfo);
            } else {
                Order order = closeOrder(cachedCopierOrderInfo, orderInfo, retry);
                retry--;
                if (order == null && retry > 0) {
                    operate(record, retry);
                }
            }
        }
    }

    /**
     * @param cachedCopierOrderInfo 缓存
     * @param orderInfo             订单信息
     * @param retry                 还可以尝试次数
     * @return 是否继续尝试 true false
     */
    Order closeOrder(CachedCopierOrderInfo cachedCopierOrderInfo, EaOrderInfo orderInfo, int retry) {
        Order order = null;
        Long orderId = copier.getId();
        try {
            FollowTraderSubscribeEntity leaderCopier = leaderCopierService.subscription(copier.getId(), orderInfo.getMasterId());
            if (leaderCopier == null) {
                throw new RuntimeException("跟随关系不存在");
            }
            double lots = cachedCopierOrderInfo.getSlavePosition();

            double closePrice = price(copierApiTrader, cachedCopierOrderInfo);
            closePrice = cachedCopierOrderInfo.getOpenPrice();
            order = copierApiTrader.orderClient.OrderClose(cachedCopierOrderInfo.getSlaveSymbol(), cachedCopierOrderInfo.getSlaveTicket().intValue(), lots, closePrice, Integer.MAX_VALUE);
            //删除redis中的缓存
            redisUtil.hDel(mapKey, Long.toString(orderInfo.getTicket()));

            log.debug("[MT4跟单者{}-{}-{}]完全平仓[{}]订单成功，[喊单者{}-{}-{}],喊单者订单信息[{}]", orderId, copier.getAccount(), copier.getServerName(), cachedCopierOrderInfo.getSlaveTicket(), orderInfo.getMasterId(), orderInfo.getAccount(), orderInfo.getServer(), orderInfo);

            BigDecimal leaderProfit = orderInfo.getSwap().add(orderInfo.getCommission()).add(orderInfo.getProfit()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal copierProfit = new BigDecimal(order.Swap + order.Commission + order.Profit).setScale(2, RoundingMode.HALF_UP);
            Duration between = Duration.between(orderInfo.getCloseTime(), order.CloseTime.plus(1, ChronoUnit.DAYS));
            long closeDelay = between.toSeconds() % 3600;
            //根据订单账户获取账户信息
            FollowTraderEntity aotfxTrader = aotfxTraderService.getOne(Wrappers.<FollowTraderEntity>lambdaQuery().eq(FollowTraderEntity::getId, copier.getId()));
            //根据账户userId获取当前用户代理userId
            String agentId = null;

            log.info("OrderClose平仓");

            openOrderMappingService.update(Wrappers.<FollowSubscribeOrderEntity>lambdaUpdate()
                    .set(FollowSubscribeOrderEntity::getFlag, CopyTradeFlag.POS)
                    .set(FollowSubscribeOrderEntity::getMasterCloseTime, orderInfo.getCloseTime())
                    .set(FollowSubscribeOrderEntity::getMasterProfit, leaderProfit)
                    .set(FollowSubscribeOrderEntity::getDetectedCloseTime, orderInfo.getDetectedCloseTime())
                    .set(FollowSubscribeOrderEntity::getSlavePosition, order.Lots)
                    .set(FollowSubscribeOrderEntity::getSlaveProfit, copierProfit)
                    .set(FollowSubscribeOrderEntity::getSlaveReceiveCloseTime, orderInfo.getSlaveReceiveCloseTime())
                    .set(FollowSubscribeOrderEntity::getSlaveCloseTime, order.CloseTime)
                    .set(FollowSubscribeOrderEntity::getExtra, "平仓成功")
                    .set(FollowSubscribeOrderEntity::getSlaveComment, order.Comment)
                    .eq(FollowSubscribeOrderEntity::getMasterId, orderInfo.getMasterId())
                    .eq(FollowSubscribeOrderEntity::getSlaveId, orderId)
                    .eq(FollowSubscribeOrderEntity::getMasterTicket, orderInfo.getTicket()));

        } catch (Exception e) {
            if (retry > 0) {
                log.error("跟单者完全平仓订单失败，再做尝试，[原因：{}],跟单者[{}-{}-{}]完全平仓{}订单失败，喊跟单者[{}-{}-{}],喊单者订单信息[{}]", e.getMessage(), orderId, copier.getAccount(), copier.getServerName(), cachedCopierOrderInfo.getSlaveTicket(), orderInfo.getMasterId(), orderInfo.getAccount(), orderInfo.getServer(), orderInfo);
            } else {
                openOrderMappingService.update(Wrappers.<FollowSubscribeOrderEntity>lambdaUpdate()
                        .set(FollowSubscribeOrderEntity::getFlag, POF)
                        .set(FollowSubscribeOrderEntity::getMasterCloseTime, orderInfo.getCloseTime())
                        .set(FollowSubscribeOrderEntity::getDetectedCloseTime, orderInfo.getDetectedCloseTime())
                        .set(FollowSubscribeOrderEntity::getExtra, "[平仓失败]" + e.getMessage())
                        .ne(FollowSubscribeOrderEntity::getFlag, CPOS)
                        .eq(FollowSubscribeOrderEntity::getMasterId, orderInfo.getMasterId())
                        .eq(FollowSubscribeOrderEntity::getSlaveId, orderId)
                        .eq(FollowSubscribeOrderEntity::getMasterTicket, orderInfo.getTicket()));
                redisUtil.hDel(mapKey, Long.toString(orderInfo.getTicket()));
                log.error("跟单者完全平仓订单最终尝试失败，[原因：{}],[跟单者{}-{}-{}]完全平仓{}订单失败，[喊单者{}-{}-{}],喊单者订单信息[{}]", e.getClass().getSimpleName(), orderId, copier.getAccount(), copier.getServerName(), cachedCopierOrderInfo.getSlaveTicket(), orderInfo.getMasterId(), orderInfo.getAccount(), orderInfo.getServer(), orderInfo);
            }
        }
        return order;
    }
}
