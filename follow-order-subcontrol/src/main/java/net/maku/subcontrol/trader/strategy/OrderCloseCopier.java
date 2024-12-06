package net.maku.subcontrol.trader.strategy;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.*;
import net.maku.followcom.enums.*;
import net.maku.followcom.pojo.EaOrderInfo;
import net.maku.followcom.util.FollowConstant;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.utils.ThreadPoolUtils;
import net.maku.subcontrol.entity.FollowOrderHistoryEntity;
import net.maku.subcontrol.entity.FollowSubscribeOrderEntity;
import net.maku.subcontrol.pojo.CachedCopierOrderInfo;
import net.maku.subcontrol.trader.AbstractApiTrader;
import net.maku.subcontrol.trader.CopierApiTrader;
import net.maku.subcontrol.trader.CopierApiTradersAdmin;
import online.mtapi.mt4.Op;
import online.mtapi.mt4.Order;
import online.mtapi.mt4.QuoteClient;
import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static net.maku.followcom.enums.CopyTradeFlag.CPOS;
import static net.maku.followcom.enums.CopyTradeFlag.POF;
import static online.mtapi.mt4.Op.Buy;


/**
 * MT跟单对象收到信号后，平仓操作
 */
@Slf4j
@Component
@AllArgsConstructor
public class OrderCloseCopier extends AbstractOperation implements IOperationStrategy {


    private final CopierApiTradersAdmin copierApiTradersAdmin;

    @Override
    public void operate(AbstractApiTrader trader, EaOrderInfo orderInfo, int flag) {
        String mapKey = trader.getTrader().getId() + "#" + trader.getTrader().getAccount();

        FollowTraderEntity copier = trader.getTrader();
        Long orderId = copier.getId();
        orderInfo.setSlaveReceiveCloseTime(orderInfo.getSlaveReceiveCloseTime() == null ? LocalDateTime.now() : orderInfo.getSlaveReceiveCloseTime());
        // 通过map获取平仓订单号
        log.debug("[MT4跟单者:{}-{}-{}]开始尝试平仓，[喊单者:{}-{}-{}],喊单者订单信息[{}]", orderId, copier.getAccount(), copier.getServerName(), orderInfo.getMasterId(), orderInfo.getAccount(), orderInfo.getServer(), orderInfo);
        // 跟单者(和当前喊单者的平仓订单)，对应的订单号。
        CachedCopierOrderInfo cachedCopierOrderInfo = (CachedCopierOrderInfo) redisUtil.hGet(Constant.FOLLOW_SUB_ORDER + mapKey, Long.toString(orderInfo.getTicket()));
        if (ObjectUtils.isEmpty(cachedCopierOrderInfo)) {
            log.info("未发现缓存"+mapKey);
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
            closeOrder(trader, cachedCopierOrderInfo, orderInfo, flag, mapKey);
        }
    }

    /**
     * @param cachedCopierOrderInfo 缓存
     * @param orderInfo             订单信息
     * @return 是否继续尝试 true false
     */
    Order closeOrder(AbstractApiTrader trader, CachedCopierOrderInfo cachedCopierOrderInfo, EaOrderInfo orderInfo, int flag, String mapKey) {
        Order order = null;
        FollowTraderEntity copier = trader.getTrader();
        Long orderId = copier.getId();
        try {
            FollowTraderSubscribeEntity leaderCopier = followTraderSubscribeService.subscription(copier.getId(), orderInfo.getMasterId());
            if (leaderCopier == null) {
                throw new RuntimeException("跟随关系不存在");
            }
            log.info("平仓信息"+cachedCopierOrderInfo);
            double lots = cachedCopierOrderInfo.getSlavePosition();
            QuoteClient quoteClient=trader.quoteClient;
            if (ObjectUtil.isEmpty(trader) || ObjectUtil.isEmpty(quoteClient)
                    || !quoteClient.Connected()) {
                copierApiTradersAdmin.removeTrader(copier.getId().toString());
                ConCodeEnum conCodeEnum = copierApiTradersAdmin.addTrader(copier);
                if (conCodeEnum == ConCodeEnum.SUCCESS ) {
                    quoteClient=copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(copier.getId().toString()).quoteClient;
                    CopierApiTrader copierApiTrader1 = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(copier.getId().toString());
                    copierApiTrader1.setTrader(copier);
                    copier=copierApiTrader1.getTrader();
                }else {
                    throw new RuntimeException("登录异常"+trader.getTrader().getId());
                }
            }
            double bid = quoteClient.GetQuote(cachedCopierOrderInfo.getSlaveSymbol()).Bid;
            double ask = quoteClient.GetQuote(cachedCopierOrderInfo.getSlaveSymbol()).Ask;
            double startPrice = trader.getTrader().getType().equals(Op.Buy.getValue()) ? bid : ask;
            LocalDateTime startTime = LocalDateTime.now();
            log.info("平仓信息记录{}:{}:{}",cachedCopierOrderInfo.getSlaveSymbol(),cachedCopierOrderInfo.getSlaveTicket(),lots);
            if (copier.getType() == Buy.getValue()) {
                order = quoteClient.OrderClient.OrderClose(cachedCopierOrderInfo.getSlaveSymbol(), cachedCopierOrderInfo.getSlaveTicket().intValue(), lots, bid, Integer.MAX_VALUE);
            } else {
                order = quoteClient.OrderClient.OrderClose(cachedCopierOrderInfo.getSlaveSymbol(), cachedCopierOrderInfo.getSlaveTicket().intValue(), lots, ask, Integer.MAX_VALUE);
            }
            LocalDateTime endTime = LocalDateTime.now();
            if (flag == 1) {
                log.info("补单平仓开始");
            }
            log.info("[MT4跟单者{}-{}-{}]完全平仓[{}]订单成功，[喊单者{}-{}-{}],喊单者订单信息[{}]", orderId, copier.getAccount(), copier.getServerName(), cachedCopierOrderInfo.getSlaveTicket(), orderInfo.getMasterId(), orderInfo.getAccount(), orderInfo.getServer(), orderInfo);

            BigDecimal leaderProfit = orderInfo.getSwap().add(orderInfo.getCommission()).add(orderInfo.getProfit()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal copierProfit = new BigDecimal(order.Swap + order.Commission + order.Profit).setScale(2, RoundingMode.HALF_UP);
            Order finalOrder = order;
            FollowTraderEntity finalCopier = copier;
            ThreadPoolUtils.getExecutor().execute(() -> {
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
                //插入历史订单
                FollowOrderHistoryEntity historyEntity = new FollowOrderHistoryEntity();
                historyEntity.setTraderId(finalCopier.getId());
                historyEntity.setAccount(finalCopier.getAccount());
                historyEntity.setOrderNo(finalOrder.Ticket);
                historyEntity.setType(finalOrder.Type.getValue());
                historyEntity.setOpenTime(finalOrder.OpenTime);
                historyEntity.setCloseTime(finalOrder.CloseTime);
                historyEntity.setSize(BigDecimal.valueOf(finalOrder.Lots));
                historyEntity.setSymbol(finalOrder.Symbol);
                historyEntity.setOpenPrice(BigDecimal.valueOf(finalOrder.OpenPrice));
                historyEntity.setClosePrice(BigDecimal.valueOf(finalOrder.ClosePrice));
                //止损
                historyEntity.setProfit(copierProfit);
                historyEntity.setComment(finalOrder.Comment);
                historyEntity.setSwap(BigDecimal.valueOf(finalOrder.Swap));
                historyEntity.setMagic(finalOrder.MagicNumber);
                historyEntity.setTp(BigDecimal.valueOf(finalOrder.TakeProfit));
                historyEntity.setSymbol(finalOrder.Symbol);
                historyEntity.setSl(BigDecimal.valueOf(finalOrder.StopLoss));
                historyEntity.setCreateTime(LocalDateTime.now());
                historyEntity.setVersion(0);
                historyEntity.setCommission(BigDecimal.valueOf(finalOrder.Commission));
                followOrderHistoryService.save(historyEntity);
                //生成日志
                FollowTraderLogEntity followTraderLogEntity = new FollowTraderLogEntity();
                followTraderLogEntity.setTraderType(TraderLogEnum.FOLLOW_OPERATION.getType());
                log.info("平仓日志" + finalCopier);
                FollowVpsEntity followVpsEntity = followVpsService.getById(finalCopier.getServerId());
                followTraderLogEntity.setVpsId(followVpsEntity.getId());
                followTraderLogEntity.setVpsClient(followVpsEntity.getClientId());
                followTraderLogEntity.setVpsName(followVpsEntity.getName());
                followTraderLogEntity.setCreateTime(LocalDateTime.now());
                followTraderLogEntity.setType(flag == 0 ? TraderLogTypeEnum.CLOSE.getType() : TraderLogTypeEnum.REPAIR.getType());
                //跟单信息
                String remark = (flag == 0 ? FollowConstant.FOLLOW_CLOSE : FollowConstant.FOLLOW_REPAIR_CLOSE) + "策略账号=" + orderInfo.getAccount() + "单号=" + orderInfo.getTicket() +
                        "跟单账号=" + finalCopier.getAccount() + ",单号=" + finalOrder.Ticket + ",品种=" + finalOrder.Symbol + ",手数=" + finalOrder.Lots + ",类型=" + finalOrder.Type.name();
                followTraderLogEntity.setLogDetail(remark);
                followTraderLogService.save(followTraderLogEntity);
                //详情
                FollowOrderDetailEntity detailServiceOne = followOrderDetailService.getOne(new LambdaQueryWrapper<FollowOrderDetailEntity>().eq(FollowOrderDetailEntity::getOrderNo, finalOrder.Ticket).eq(FollowOrderDetailEntity::getIpAddr, FollowConstant.LOCAL_HOST));
                if (ObjectUtil.isNotEmpty(detailServiceOne)) {
                    log.info("记录详情"+detailServiceOne.getTraderId()+"订单"+detailServiceOne.getOrderNo());
                    updateCloseOrder(detailServiceOne, finalOrder, startTime, endTime, startPrice);
                }
                //删除redis中的缓存
                redisUtil.hDel(Constant.FOLLOW_SUB_ORDER + mapKey, Long.toString(orderInfo.getTicket()));
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
            redisUtil.hDel(Constant.FOLLOW_SUB_ORDER + mapKey, Long.toString(orderInfo.getTicket()));
            log.error("跟单者完全平仓订单最终尝试失败，[原因：{}],[跟单者{}-{}-{}]完全平仓{}订单失败，[喊单者{}-{}-{}],喊单者订单信息[{}]", e.getMessage(), orderId, copier.getAccount(), copier.getServerName(), cachedCopierOrderInfo.getSlaveTicket(), orderInfo.getMasterId(), orderInfo.getAccount(), orderInfo.getServer(), orderInfo);
        }
        return order;
    }

    private void updateCloseOrder(FollowOrderDetailEntity followOrderDetailEntity, Order order, LocalDateTime startTime, LocalDateTime endTime, double price) {
        //保存平仓信息
        followOrderDetailEntity.setRequestCloseTime(startTime);
        followOrderDetailEntity.setResponseCloseTime(endTime);
        followOrderDetailEntity.setCloseTime(order.CloseTime);
        followOrderDetailEntity.setClosePrice(BigDecimal.valueOf(order.ClosePrice));
        followOrderDetailEntity.setRequestClosePrice(new BigDecimal(price));
        followOrderDetailEntity.setSwap(BigDecimal.valueOf(order.Swap));
        followOrderDetailEntity.setCommission(BigDecimal.valueOf(order.Commission));
        followOrderDetailEntity.setProfit(BigDecimal.valueOf(order.Profit));
        followOrderDetailEntity.setCloseStatus(CloseOrOpenEnum.OPEN.getValue());

        //获取symbol信息
        Map<String, FollowSysmbolSpecificationEntity> specificationEntityMap = getSymbolSpecification(followOrderDetailEntity.getTraderId());
        FollowSysmbolSpecificationEntity followSysmbolSpecificationEntity = specificationEntityMap.get(followOrderDetailEntity.getSymbol());
        BigDecimal hd;
        if (followSysmbolSpecificationEntity.getProfitMode().equals("Forex")) {
            //如果forex 并包含JPY 也是100
            if (followOrderDetailEntity.getSymbol().contains("JPY")) {
                hd = new BigDecimal("100");
            } else {
                hd = new BigDecimal("10000");
            }
        } else {
            //如果非forex 都是 100
            hd = new BigDecimal("100");
        }
        long seconds = DateUtil.between(DateUtil.date(followOrderDetailEntity.getResponseCloseTime()), DateUtil.date(followOrderDetailEntity.getRequestCloseTime()), DateUnit.MS);
        followOrderDetailEntity.setCloseTimeDifference((int) seconds);
        followOrderDetailEntity.setClosePriceSlip(followOrderDetailEntity.getClosePrice().subtract(followOrderDetailEntity.getRequestClosePrice()).multiply(hd).abs());
        followOrderDetailService.updateById(followOrderDetailEntity);
    }

    private Map<String, FollowSysmbolSpecificationEntity> getSymbolSpecification(long traderId) {
        //获取symbol信息
        List<FollowSysmbolSpecificationEntity> followSysmbolSpecificationEntityList;
        if (ObjectUtil.isNotEmpty(redisUtil.get(Constant.SYMBOL_SPECIFICATION + traderId))) {
            followSysmbolSpecificationEntityList = (List<FollowSysmbolSpecificationEntity>) redisUtil.get(Constant.SYMBOL_SPECIFICATION + traderId);
        } else {
            //查询改账号的品种规格
            followSysmbolSpecificationEntityList = followSysmbolSpecificationService.list(new LambdaQueryWrapper<FollowSysmbolSpecificationEntity>().eq(FollowSysmbolSpecificationEntity::getTraderId, traderId));
            redisUtil.set(Constant.SYMBOL_SPECIFICATION + traderId, followSysmbolSpecificationEntityList);
        }
        return followSysmbolSpecificationEntityList.stream().collect(Collectors.toMap(FollowSysmbolSpecificationEntity::getSymbol, i -> i));
    }

    }