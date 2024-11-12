package net.maku.subcontrol.trader.strategy;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.entity.FollowTraderLogEntity;
import net.maku.followcom.entity.FollowTraderSubscribeEntity;
import net.maku.followcom.entity.FollowVpsEntity;
import net.maku.followcom.enums.CopyTradeFlag;
import net.maku.followcom.enums.TraderLogEnum;
import net.maku.followcom.enums.TraderLogTypeEnum;
import net.maku.followcom.pojo.EaOrderInfo;
import net.maku.followcom.service.FollowTraderService;
import net.maku.followcom.util.FollowConstant;
import net.maku.framework.common.constant.Constant;
import net.maku.subcontrol.entity.FollowOrderHistoryEntity;
import net.maku.subcontrol.entity.FollowSubscribeOrderEntity;
import net.maku.subcontrol.pojo.CachedCopierOrderInfo;
import net.maku.subcontrol.trader.CopierApiTrader;
import online.mtapi.mt4.Op;
import online.mtapi.mt4.Order;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.util.ObjectUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

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
        //处理即时跟平
        int flag=0;
        if (record.key().equals("close")){
            flag=1;
        }
        EaOrderInfo orderInfo = (EaOrderInfo) record.value();
        orderInfo.setSlaveReceiveCloseTime(orderInfo.getSlaveReceiveCloseTime() == null ? LocalDateTime.now() : orderInfo.getSlaveReceiveCloseTime());
        // 通过map获取平仓订单号
        log.debug("[MT4跟单者:{}-{}-{}]开始尝试平仓，[喊单者:{}-{}-{}],喊单者订单信息[{}]", orderId, copier.getAccount(), copier.getServerName(), orderInfo.getMasterId(), orderInfo.getAccount(), orderInfo.getServer(), orderInfo);
        // 跟单者(和当前喊单者的平仓订单)，对应的订单号。
        CachedCopierOrderInfo cachedCopierOrderInfo = (CachedCopierOrderInfo) redisUtil.hGet(Constant.FOLLOW_SUB_ORDER+mapKey, Long.toString(orderInfo.getTicket()));
        if (ObjectUtils.isEmpty(cachedCopierOrderInfo)) {
            FollowSubscribeOrderEntity openOrderMapping = openOrderMappingService.getOne(Wrappers.<FollowSubscribeOrderEntity>lambdaQuery()
                    .eq(FollowSubscribeOrderEntity::getMasterId, orderInfo.getMasterId())
                    .eq(FollowSubscribeOrderEntity::getSlaveId, orderId)
                    .eq(FollowSubscribeOrderEntity::getMasterTicket, orderInfo.getTicket()));
            if (!ObjectUtils.isEmpty(openOrderMapping)) {
                cachedCopierOrderInfo = new CachedCopierOrderInfo(openOrderMapping);
            } else {
                cachedCopierOrderInfo = new CachedCopierOrderInfo();
            }
        }
        if (ObjectUtils.isEmpty(cachedCopierOrderInfo.getSlaveTicket())) {
            log.error("[MT4跟单者:{}-{}-{}]没有找到对应平仓订单号,因为该对应的订单开仓失败，[喊单者:{}-{}-{}],喊单者订单信息[{}]", orderId, copier.getAccount(), copier.getServerName(), orderInfo.getMasterId(), orderInfo.getAccount(), orderInfo.getServer(), orderInfo);
        } else {
            Order order = closeOrder(cachedCopierOrderInfo, orderInfo, retry,flag);
            retry--;
            if (order == null && retry > 0) {
                operate(record, retry);
            }
        }
    }

    /**
     * @param cachedCopierOrderInfo 缓存
     * @param orderInfo             订单信息
     * @param retry                 还可以尝试次数
     * @return 是否继续尝试 true false
     */
    Order closeOrder(CachedCopierOrderInfo cachedCopierOrderInfo, EaOrderInfo orderInfo, int retry,int flag) {
        Order order = null;
        Long orderId = copier.getId();
        try {
            FollowTraderSubscribeEntity leaderCopier = leaderCopierService.subscription(copier.getId(), orderInfo.getMasterId());
            if (leaderCopier == null) {
                throw new RuntimeException("跟随关系不存在");
            }
            double lots = cachedCopierOrderInfo.getSlavePosition();

            order = copierApiTrader.orderClient.OrderClose(cachedCopierOrderInfo.getSlaveSymbol(), cachedCopierOrderInfo.getSlaveTicket().intValue(), lots, 0, Integer.MAX_VALUE);
            //删除redis中的缓存
            redisUtil.hDel(Constant.FOLLOW_SUB_ORDER+mapKey, Long.toString(orderInfo.getTicket()));

            if (flag==1){
                log.info("补单平仓开始");
            }
            log.info("[MT4跟单者{}-{}-{}]完全平仓[{}]订单成功，[喊单者{}-{}-{}],喊单者订单信息[{}]", orderId, copier.getAccount(), copier.getServerName(), cachedCopierOrderInfo.getSlaveTicket(), orderInfo.getMasterId(), orderInfo.getAccount(), orderInfo.getServer(), orderInfo);

            BigDecimal leaderProfit = orderInfo.getSwap().add(orderInfo.getCommission()).add(orderInfo.getProfit()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal copierProfit = new BigDecimal(order.Swap + order.Commission + order.Profit).setScale(2, RoundingMode.HALF_UP);

            log.info("OrderClose平仓{}",order.Ticket);

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
            //生成历史订单
            log.info("生成历史订单"+orderInfo.getTicket());
            FollowOrderHistoryEntity followOrderHistory=new FollowOrderHistoryEntity();
            followOrderHistory.setTraderId(copier.getId());
            followOrderHistory.setAccount(copier.getAccount());
            followOrderHistory.setOrderNo(order.Ticket);
            followOrderHistory.setClosePrice(BigDecimal.valueOf(order.ClosePrice));
            followOrderHistory.setOpenPrice(BigDecimal.valueOf(order.OpenPrice));
            followOrderHistory.setOpenTime(order.OpenTime);
            followOrderHistory.setCloseTime(order.CloseTime);
            followOrderHistory.setProfit(copierProfit);
            followOrderHistory.setComment(order.Comment);
            followOrderHistory.setSize(BigDecimal.valueOf(order.Lots));
            followOrderHistory.setType(order.Type.getValue());
            followOrderHistory.setSwap(BigDecimal.valueOf(order.Swap));
            followOrderHistory.setMagic(order.MagicNumber);
            followOrderHistory.setTp(BigDecimal.valueOf(order.TakeProfit));
            followOrderHistory.setSymbol(order.Symbol);
            followOrderHistory.setSl(BigDecimal.valueOf(order.StopLoss));
            followOrderHistory.setCreateTime(LocalDateTime.now());
            followOrderHistoryService.save(followOrderHistory);
            //生成日志
            FollowTraderLogEntity followTraderLogEntity = new FollowTraderLogEntity();
            followTraderLogEntity.setTraderType(TraderLogEnum.FOLLOW_OPERATION.getType());
            FollowTraderEntity trader = followTraderService.getById(copier.getId());
            FollowVpsEntity followVpsEntity = followVpsService.getById(trader.getServerId());
            followTraderLogEntity.setVpsId(followVpsEntity.getId());
            followTraderLogEntity.setVpsClient(followVpsEntity.getClientId());
            followTraderLogEntity.setVpsName(followVpsEntity.getName());
            followTraderLogEntity.setCreateTime(LocalDateTime.now());
            followTraderLogEntity.setType(flag==0?TraderLogTypeEnum.CLOSE.getType():TraderLogTypeEnum.REPAIR.getType());
            //跟单信息
            String remark = (flag == 0 ? FollowConstant.FOLLOW_CLOSE : FollowConstant.FOLLOW_REPAIR_CLOSE) + "策略账号=" + orderInfo.getAccount() + "单号=" + orderInfo.getTicket() +
                    "跟单账号=" + trader.getAccount() + ",单号=" + order.Ticket + ",品种=" + order.Symbol + ",手数=" + order.Lots + ",类型=" + order.Type.name();
            followTraderLogEntity.setLogDetail(remark);
            followTraderLogService.save(followTraderLogEntity);

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
                redisUtil.hDel(Constant.FOLLOW_SUB_ORDER+mapKey, Long.toString(orderInfo.getTicket()));
                log.error("跟单者完全平仓订单最终尝试失败，[原因：{}],[跟单者{}-{}-{}]完全平仓{}订单失败，[喊单者{}-{}-{}],喊单者订单信息[{}]", e.getClass().getSimpleName(), orderId, copier.getAccount(), copier.getServerName(), cachedCopierOrderInfo.getSlaveTicket(), orderInfo.getMasterId(), orderInfo.getAccount(), orderInfo.getServer(), orderInfo);
            }
        }
        return order;
    }
}
