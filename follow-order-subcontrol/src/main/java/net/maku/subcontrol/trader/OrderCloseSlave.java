package net.maku.subcontrol.trader;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cld.utils.date.ThreeStrategyDateUtil;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowOrderActiveEntity;
import net.maku.followcom.entity.FollowSubscribeOrderEntity;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.entity.FollowTraderSubscribeEntity;
import net.maku.followcom.enums.AcEnum;
import net.maku.followcom.enums.CopyTradeFlag;
import net.maku.followcom.service.FollowTraderService;
import net.maku.framework.common.exception.ServerException;
import net.maku.subcontrol.pojo.CachedCopierOrderInfo;
import net.maku.subcontrol.pojo.EaOrderInfo;
import net.maku.subcontrol.service.IOperationStrategy;
import online.mtapi.mt4.Exception.ConnectException;
import online.mtapi.mt4.Exception.TimeoutException;
import online.mtapi.mt4.Order;
import online.mtapi.mt4.QuoteClient;
import online.mtapi.mt4.QuoteEventArgs;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.util.ObjectUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;

import static net.maku.followcom.enums.CopyTradeFlag.CPOS;
import static net.maku.followcom.enums.CopyTradeFlag.POF;


/**
 * MT跟单对象收到信号后，平仓操作
 */
@Slf4j
public class OrderCloseSlave extends AbstractOperation implements IOperationStrategy {
    FollowTraderEntity copier;
    CopierApiTrader copierApiTrader;
    FollowTraderService aotfxTraderService;

    public OrderCloseSlave(CopierApiTrader copierApiTrader) {
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
            FollowOrderActiveEntity activeOrder = (FollowOrderActiveEntity) record.value();
            leaderCopier = leaderCopierService.subscription(orderId, activeOrder.getTraderId());
            closeCycle(leaderCopier, activeOrder);
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
            FollowTraderSubscribeEntity leaderCopier = leaderCopierService.subscription(copier.getId(), Long.valueOf(orderInfo.getMasterId()));
            if (leaderCopier == null) {
                throw new ServerException("跟随关系不存在");
            } else if (!leaderCopier.getCreateTime().isBefore(cachedCopierOrderInfo.getOpenTime())) {
                throw new ServerException("非本次订阅");
            }
            double lots=cachedCopierOrderInfo.getSlavePosition();


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

    /**
     * 循环平仓校验后发出强制平仓信号
     *
     * @param activeOrder 跟单者需要强平的订单
     */
    private void closeCycle(FollowTraderSubscribeEntity leaderCopier, FollowOrderActiveEntity activeOrder) {
        try {
            if (leaderCopier == null) {
                throw new ServerException("跟随关系不存在");
            } else if (leaderCopier.getCreateTime().isBefore(activeOrder.getOpenTime())) {
                throw new ServerException("非本次订阅");
            }
            QuoteEventArgs quoteEventArgs = copierApiTrader.quoteClient.GetQuote(activeOrder.getSymbol());
            while (quoteEventArgs == null && copierApiTrader.quoteClient.Connected()) {
                Thread.sleep(50);
                copierApiTrader.quoteClient.Subscribe(activeOrder.getSymbol());
                quoteEventArgs = copierApiTrader.quoteClient.GetQuote(activeOrder.getSymbol());
            }
            Order order = copierApiTrader.orderClient.OrderClose(activeOrder.getSymbol(), activeOrder.getOrderNo().intValue(), activeOrder.getSize().doubleValue(), activeOrder.getType() == 0 ? quoteEventArgs.Bid : quoteEventArgs.Ask, Integer.MAX_VALUE);
            BigDecimal profit = BigDecimal.valueOf(order.Swap + order.Commission + order.Profit);
            openOrderMappingService.update(Wrappers.<FollowSubscribeOrderEntity>lambdaUpdate()
                    .eq(FollowSubscribeOrderEntity::getSlaveId, copier.getId())
                    .eq(FollowSubscribeOrderEntity::getSlaveTicket, activeOrder.getOrderNo())
                    .set(FollowSubscribeOrderEntity::getSlaveOpenTime, order.OpenTime)
                    .set(FollowSubscribeOrderEntity::getSlaveCloseTime, order.CloseTime)
                    .set(FollowSubscribeOrderEntity::getSlaveProfit, profit)
                    .set(FollowSubscribeOrderEntity::getFlag, CPOS)
                    .set(FollowSubscribeOrderEntity::getExtra, "循环平仓成功"));
        } catch (Exception e) {
            try {
                QuoteClient qc = new QuoteClient(copierApiTrader.quoteClient.User, copierApiTrader.quoteClient.Password, copierApiTrader.quoteClient.Host, copierApiTrader.quoteClient.Port);
                qc.Connect();
                boolean anyMatch = Arrays.stream(qc.GetOpenedOrders()).anyMatch(orderInfo -> orderInfo.Ticket == activeOrder.getOrderNo());
                if (anyMatch) {
                    openOrderMappingService.update(Wrappers.<FollowSubscribeOrderEntity>lambdaUpdate()
                            .eq(FollowSubscribeOrderEntity::getSlaveId, copier.getId())
                            .eq(FollowSubscribeOrderEntity::getSlaveTicket, activeOrder.getOrderNo())
                            .set(FollowSubscribeOrderEntity::getExtra, "循环平仓失败:" + e.getMessage()));
                } else {
                    openOrderMappingService.update(Wrappers.<FollowSubscribeOrderEntity>lambdaUpdate()
                            .eq(FollowSubscribeOrderEntity::getSlaveId, copier.getId())
                            .eq(FollowSubscribeOrderEntity::getSlaveTicket, activeOrder.getOrderNo())
                            .set(FollowSubscribeOrderEntity::getExtra, "循环平仓成功，已经平仓"));


                }
            } catch (IOException | ConnectException | TimeoutException ex) {
                throw new RuntimeException(ex);
            }
        }
    }
}
