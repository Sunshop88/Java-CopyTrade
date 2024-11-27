package net.maku.subcontrol.trader.strategy;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.entity.FollowTraderLogEntity;
import net.maku.followcom.entity.FollowTraderSubscribeEntity;
import net.maku.followcom.entity.FollowVpsEntity;
import net.maku.followcom.enums.CopyTradeFlag;
import net.maku.followcom.enums.TraderLogEnum;
import net.maku.followcom.enums.TraderLogTypeEnum;
import net.maku.followcom.pojo.EaOrderInfo;
import net.maku.followcom.service.FollowTraderLogService;
import net.maku.followcom.service.FollowTraderService;
import net.maku.followcom.service.FollowTraderSubscribeService;
import net.maku.followcom.service.FollowVpsService;
import net.maku.followcom.util.FollowConstant;
import net.maku.framework.common.cache.RedisUtil;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.utils.ThreadPoolUtils;
import net.maku.subcontrol.entity.FollowOrderHistoryEntity;
import net.maku.subcontrol.entity.FollowSubscribeOrderEntity;
import net.maku.subcontrol.pojo.CachedCopierOrderInfo;
import net.maku.subcontrol.service.FollowOrderHistoryService;
import net.maku.subcontrol.service.FollowSubscribeOrderService;
import net.maku.subcontrol.trader.AbstractApiTrader;
import net.maku.subcontrol.trader.CopierApiTrader;
import online.mtapi.mt4.Op;
import online.mtapi.mt4.Order;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;
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
@Component
@AllArgsConstructor
public class OrderCloseCopier extends AbstractOperation implements IOperationStrategy {


    @Override
    public void operate(AbstractApiTrader trader,EaOrderInfo orderInfo,int flag) {
        String mapKey = trader.getTrader().getId() + "#" + trader.getTrader().getAccount();

        FollowTraderEntity copier=trader.getTrader();
        Long orderId = copier.getId();
        orderInfo.setSlaveReceiveCloseTime(orderInfo.getSlaveReceiveCloseTime() == null ? LocalDateTime.now() : orderInfo.getSlaveReceiveCloseTime());
        // 通过map获取平仓订单号
        log.debug("[MT4跟单者:{}-{}-{}]开始尝试平仓，[喊单者:{}-{}-{}],喊单者订单信息[{}]", orderId, copier.getAccount(), copier.getServerName(), orderInfo.getMasterId(), orderInfo.getAccount(), orderInfo.getServer(), orderInfo);
        // 跟单者(和当前喊单者的平仓订单)，对应的订单号。
        CachedCopierOrderInfo cachedCopierOrderInfo = (CachedCopierOrderInfo) redisUtil.hGet(Constant.FOLLOW_SUB_ORDER+mapKey, Long.toString(orderInfo.getTicket()));
        if (ObjectUtils.isEmpty(cachedCopierOrderInfo)) {
            FollowSubscribeOrderEntity openOrderMapping = followSubscribeOrderService.getOne(Wrappers.<FollowSubscribeOrderEntity>lambdaQuery()
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
             closeOrder(trader,cachedCopierOrderInfo, orderInfo,flag,mapKey);
        }
    }

    /**
     * @param cachedCopierOrderInfo 缓存
     * @param orderInfo             订单信息
     * @return 是否继续尝试 true false
     */
    Order closeOrder(AbstractApiTrader trader,CachedCopierOrderInfo cachedCopierOrderInfo, EaOrderInfo orderInfo,int flag,String mapKey) {
        Order order = null;
        FollowTraderEntity copier=trader.getTrader();
        Long orderId = copier.getId();
        try {
            FollowTraderSubscribeEntity leaderCopier = followTraderSubscribeService.subscription(copier.getId(), orderInfo.getMasterId());
            if (leaderCopier == null) {
                throw new RuntimeException("跟随关系不存在");
            }
            double lots = cachedCopierOrderInfo.getSlavePosition();

            order = trader.orderClient.OrderClose(cachedCopierOrderInfo.getSlaveSymbol(), cachedCopierOrderInfo.getSlaveTicket().intValue(), lots, 0, Integer.MAX_VALUE);

            if (flag==1){
                log.info("补单平仓开始");
            }
            log.info("[MT4跟单者{}-{}-{}]完全平仓[{}]订单成功，[喊单者{}-{}-{}],喊单者订单信息[{}]", orderId, copier.getAccount(), copier.getServerName(), cachedCopierOrderInfo.getSlaveTicket(), orderInfo.getMasterId(), orderInfo.getAccount(), orderInfo.getServer(), orderInfo);

            BigDecimal leaderProfit = orderInfo.getSwap().add(orderInfo.getCommission()).add(orderInfo.getProfit()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal copierProfit = new BigDecimal(order.Swap + order.Commission + order.Profit).setScale(2, RoundingMode.HALF_UP);
            Order finalOrder = order;
            ThreadPoolUtils.getExecutor().execute(()->{
                log.info("OrderClose平仓{}", finalOrder.Ticket);
                followSubscribeOrderService.update(Wrappers.<FollowSubscribeOrderEntity>lambdaUpdate()
                        .set(FollowSubscribeOrderEntity::getFlag, CopyTradeFlag.POS)
                        .set(FollowSubscribeOrderEntity::getMasterCloseTime, orderInfo.getCloseTime())
                        .set(FollowSubscribeOrderEntity::getMasterProfit, leaderProfit)
                        .set(FollowSubscribeOrderEntity::getDetectedCloseTime, orderInfo.getDetectedCloseTime())
                        .set(FollowSubscribeOrderEntity::getSlavePosition, finalOrder.Lots)
                        .set(FollowSubscribeOrderEntity::getSlaveProfit, copierProfit)
                        .set(FollowSubscribeOrderEntity::getSlaveReceiveCloseTime, orderInfo.getSlaveReceiveCloseTime())
                        .set(FollowSubscribeOrderEntity::getSlaveCloseTime, finalOrder.CloseTime)
                        .set(FollowSubscribeOrderEntity::getExtra, "平仓成功")
                        .set(FollowSubscribeOrderEntity::getSlaveComment, finalOrder.Comment)
                        .eq(FollowSubscribeOrderEntity::getMasterId, orderInfo.getMasterId())
                        .eq(FollowSubscribeOrderEntity::getSlaveId, orderId)
                        .eq(FollowSubscribeOrderEntity::getMasterTicket, orderInfo.getTicket()));
                //生成历史订单
                log.info("生成历史订单"+orderInfo.getTicket());
                FollowOrderHistoryEntity followOrderHistory=new FollowOrderHistoryEntity();
                followOrderHistory.setTraderId(copier.getId());
                followOrderHistory.setAccount(copier.getAccount());
                followOrderHistory.setOrderNo(finalOrder.Ticket);
                followOrderHistory.setClosePrice(BigDecimal.valueOf(finalOrder.ClosePrice));
                followOrderHistory.setOpenPrice(BigDecimal.valueOf(finalOrder.OpenPrice));
                followOrderHistory.setOpenTime(finalOrder.OpenTime);
                followOrderHistory.setCloseTime(finalOrder.CloseTime);
                followOrderHistory.setProfit(copierProfit);
                followOrderHistory.setComment(finalOrder.Comment);
                followOrderHistory.setSize(BigDecimal.valueOf(finalOrder.Lots));
                followOrderHistory.setType(finalOrder.Type.getValue());
                followOrderHistory.setSwap(BigDecimal.valueOf(finalOrder.Swap));
                followOrderHistory.setMagic(finalOrder.MagicNumber);
                followOrderHistory.setTp(BigDecimal.valueOf(finalOrder.TakeProfit));
                followOrderHistory.setSymbol(finalOrder.Symbol);
                followOrderHistory.setSl(BigDecimal.valueOf(finalOrder.StopLoss));
                followOrderHistory.setCreateTime(LocalDateTime.now());
                followOrderHistoryService.save(followOrderHistory);
                //生成日志
                FollowTraderLogEntity followTraderLogEntity = new FollowTraderLogEntity();
                followTraderLogEntity.setTraderType(TraderLogEnum.FOLLOW_OPERATION.getType());
                FollowVpsEntity followVpsEntity = followVpsService.getById(copier.getServerId());
                followTraderLogEntity.setVpsId(followVpsEntity.getId());
                followTraderLogEntity.setVpsClient(followVpsEntity.getClientId());
                followTraderLogEntity.setVpsName(followVpsEntity.getName());
                followTraderLogEntity.setCreateTime(LocalDateTime.now());
                followTraderLogEntity.setType(flag==0?TraderLogTypeEnum.CLOSE.getType():TraderLogTypeEnum.REPAIR.getType());
                //跟单信息
                String remark = (flag == 0 ? FollowConstant.FOLLOW_CLOSE : FollowConstant.FOLLOW_REPAIR_CLOSE) + "策略账号=" + orderInfo.getAccount() + "单号=" + orderInfo.getTicket() +
                        "跟单账号=" + copier.getAccount() + ",单号=" + finalOrder.Ticket + ",品种=" + finalOrder.Symbol + ",手数=" + finalOrder.Lots + ",类型=" + finalOrder.Type.name();
                followTraderLogEntity.setLogDetail(remark);
                followTraderLogService.save(followTraderLogEntity);
                //删除redis中的缓存
                redisUtil.hDel(Constant.FOLLOW_SUB_ORDER+mapKey, Long.toString(orderInfo.getTicket()));
            });
        } catch (Exception e) {
            followSubscribeOrderService.update(Wrappers.<FollowSubscribeOrderEntity>lambdaUpdate()
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
        return order;
    }
}
