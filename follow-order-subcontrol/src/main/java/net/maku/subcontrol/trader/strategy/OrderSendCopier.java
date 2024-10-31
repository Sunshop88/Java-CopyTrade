package net.maku.subcontrol.trader.strategy;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.*;
import net.maku.followcom.enums.CopyTradeFlag;
import net.maku.followcom.pojo.EaOrderInfo;
import net.maku.followcom.service.FollowPlatformService;
import net.maku.followcom.service.FollowTraderService;
import net.maku.followcom.service.FollowVarietyService;
import net.maku.framework.common.constant.Constant;
import net.maku.subcontrol.pojo.CachedCopierOrderInfo;
import net.maku.subcontrol.pojo.LiveOrderInfo;
import net.maku.subcontrol.rule.AbstractFollowRule;
import net.maku.subcontrol.rule.FollowRule;
import net.maku.subcontrol.service.IOperationStrategy;
import net.maku.subcontrol.trader.AbstractApiTrader;
import net.maku.subcontrol.trader.AbstractOperation;
import net.maku.subcontrol.trader.CopierApiTrader;
import online.mtapi.mt4.Exception.TimeoutException;
import online.mtapi.mt4.Op;
import online.mtapi.mt4.Order;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * MT4 跟单者处理开仓信号策略
 */
@Slf4j
public class OrderSendCopier extends AbstractOperation implements IOperationStrategy {
    FollowTraderEntity copier;
    FollowRule followRule;
    CopierApiTrader copierApiTrader;
    public OrderSendCopier(CopierApiTrader copierApiTrader) {
        super(copierApiTrader.getTrader());
        this.copierApiTrader = copierApiTrader;
        this.copier = this.copierApiTrader.getTrader();
        this.followRule = new FollowRule();
    }

    /**
     * 收到开仓信号处理操作
     *
     * @param consumerRecord ConsumerRecord
     * @param retry          处理次数
     */
    @Override
    public void operate(ConsumerRecord<String, Object> consumerRecord, int retry) {
        EaOrderInfo orderInfo = (EaOrderInfo) consumerRecord.value();
        orderInfo.setSlaveReceiveOpenTime(LocalDateTime.now());
        FollowTraderSubscribeEntity leaderCopier = leaderCopierService.subscription(copier.getId(), orderInfo.getMasterId());
        //查看喊单账号信息
        FollowTraderEntity followTraderEntity = followTraderService.getById(orderInfo.getMasterId());
        FollowPlatformEntity followPlatform = followPlatformService.getById(followTraderEntity.getPlatformId());
        // 查看品种匹配
        List<FollowVarietyEntity> followVarietyEntityList ;
        if (ObjectUtil.isNotEmpty(redisUtil.get(Constant.TRADER_VARIETY))){
            followVarietyEntityList = (List<FollowVarietyEntity>)redisUtil.get(Constant.TRADER_VARIETY);
        }else {
            followVarietyEntityList= followVarietyService.list();
            redisUtil.set(Constant.TRADER_VARIETY,followVarietyEntityList);
        }
        List<FollowVarietyEntity> collect = followVarietyEntityList.stream().filter(o -> o.getBrokerSymbol().equals(orderInfo.getOriSymbol())&&o.getBrokerName().equals(followPlatform.getBrokerName())).collect(Collectors.toList());
        if (ObjectUtil.isNotEmpty(collect)){
            //获得跟单账号对应品种
            FollowTraderEntity copyTrade = followTraderService.getById(copier.getId());
            FollowPlatformEntity copyPlat = followPlatformService.getById(copyTrade.getPlatformId());
            List<FollowVarietyEntity> collectCopy = followVarietyEntityList.stream().filter(o -> o.getStdSymbol().equals(collect.get(0).getStdSymbol()) && o.getBrokerName().equals(copyPlat.getBrokerName())).collect(Collectors.toList());
            List<String> symbolList = orderInfo.getSymbolList();
            collectCopy.forEach(o-> {
                if(ObjectUtil.isNotEmpty(o.getBrokerSymbol())){
                    symbolList.add(o.getBrokerSymbol());
                }
            });
            if (ObjectUtil.isNotEmpty(collectCopy)){
                orderInfo.setSymbolList(symbolList);
            }
        }
        if (ObjectUtil.isEmpty(orderInfo.getSymbolList())){
            try{
                //如果没有此品种匹配，校验是否可以获取报价
                if (ObjectUtil.isEmpty(copierApiTrader.quoteClient.GetQuote(orderInfo.getOriSymbol()))){
                    //订阅
                    copierApiTrader.quoteClient.Subscribe(orderInfo.getOriSymbol());
                }
                copierApiTrader.quoteClient.GetQuote(orderInfo.getOriSymbol());
            } catch (Exception e) {
                log.info("品种异常,不可下单{}+++++++账号{}" , orderInfo.getOriSymbol(),copier.getId());
            }
            orderInfo.setSymbolList(Collections.singletonList(orderInfo.getOriSymbol()));
        }

        FollowSubscribeOrderEntity openOrderMapping = new FollowSubscribeOrderEntity(orderInfo, copier);
        //  依次对备选品种进行开仓尝试
        for (String symbol : orderInfo.getSymbolList()) {
            orderInfo.setSymbol(symbol);
            AbstractFollowRule.PermitInfo permitInfo = this.followRule.permit(leaderCopier, orderInfo, copierApiTrader);
            openOrderMapping.setSlaveSymbol(orderInfo.getSymbol());
            openOrderMapping.setLeaderCopier(leaderCopier);
            openOrderMapping.setSlaveLots(BigDecimal.valueOf(permitInfo.getLots()));
            openOrderMapping.setFlag(permitInfo.getPermit());
            openOrderMapping.setExtra("[开仓]" + permitInfo.getExtra());
            log.info("开仓1========"+ permitInfo.getExtra());
            if (sendOrder(orderInfo, leaderCopier, openOrderMapping)) {
                break;
            }
            openOrderMappingService.saveOrUpdate(openOrderMapping, Wrappers.<FollowSubscribeOrderEntity>lambdaUpdate().eq(FollowSubscribeOrderEntity::getMasterId, openOrderMapping.getMasterId()).eq(FollowSubscribeOrderEntity::getMasterTicket, openOrderMapping.getMasterTicket()).eq(FollowSubscribeOrderEntity::getSlaveId, openOrderMapping.getSlaveId()));
        }
    }

    /**
     * 跟单者进行开仓
     *
     * @param orderInfo        喊单者订单信息
     * @param leaderCopier     订阅关系
     * @param openOrderMapping 开仓映射关系
     * @return true-开仓结果 true-成功 false-失败
     */
    boolean sendOrder(EaOrderInfo orderInfo, FollowTraderSubscribeEntity leaderCopier, FollowSubscribeOrderEntity openOrderMapping) {
        boolean send = Boolean.FALSE;
        //开单所需要的信息
        String symbol = orderInfo.getSymbol();
        Op op = op(orderInfo, leaderCopier);
        double lots = openOrderMapping.getSlaveLots().doubleValue();
        String comment = comment(orderInfo);
        int magic = Integer.parseInt(orderInfo.getAccount().trim());
        Order order;
        switch (op) {
            case Buy:
            case Sell:
                try {
                    log.info("[MT4跟单者:{}-{}-{}]收到订单{},开始下单,下单品种:{},下单手数:{}", copier.getId(), copier.getAccount(), copier.getServerName(), orderInfo, symbol, lots);
                    try {
                        double price = 0.0;
                        order = copierApiTrader.orderClient.OrderSend(symbol, op, lots, price, Integer.MAX_VALUE, BigDecimal.ZERO.doubleValue(), BigDecimal.ZERO.doubleValue(), comment.toUpperCase(), magic, null);
                    } catch (TimeoutException e) {
                        log.info("超时下单订单账户{}+++++原喊单账号{}+++++原喊单订单{}",copier.getAccount(),orderInfo.getMasterId(),orderInfo.getTicket());
                        //验证是否已下单
                        order = Arrays.stream(copierApiTrader.quoteClient.GetOpenedOrders()).filter(o -> o.Comment.equalsIgnoreCase(comment)).findFirst().orElse(null);
                        if (order == null) {
                            throw new RuntimeException(e);
                        }
                    }
                    send = Boolean.TRUE;
                    log.debug("[MT4跟单者:{}-{}-{}]收到订单{},结束下单", copier.getId(), copier.getAccount(), copier.getServerName(), orderInfo);
                    openOrderMapping.setCopierOrder(order, orderInfo);
                    openOrderMapping.setFlag(CopyTradeFlag.OS);
                    openOrderMapping.setExtra("[开仓]即时价格成交");

                    //缓存跟单者的开仓信息
                    CachedCopierOrderInfo cachedCopierOrderInfo = new CachedCopierOrderInfo(order);
                    redisUtil.hset(mapKey, Long.toString(orderInfo.getTicket()), cachedCopierOrderInfo, 0);
                } catch (Exception exception) {
                    if (AbstractApiTrader.availableException4.contains(exception.getMessage())) {
                        log.info(exception.getMessage());
                    } else {
                        exception.printStackTrace();
                    }
                    openOrderMapping.setSlaveType(op.getValue());
                    openOrderMapping.setSlaveTicket(null);
                    openOrderMapping.setFlag(CopyTradeFlag.OF1);
                    openOrderMapping.setExtra("[开仓]即时价格开仓失败" + exception.getMessage());
                }
                openOrderMappingService.saveOrUpdate(openOrderMapping, Wrappers.<FollowSubscribeOrderEntity>lambdaUpdate().eq(FollowSubscribeOrderEntity::getMasterId, openOrderMapping.getMasterId()).eq(FollowSubscribeOrderEntity::getMasterTicket, openOrderMapping.getMasterTicket()).eq(FollowSubscribeOrderEntity::getSlaveId, openOrderMapping.getSlaveId()));
                break;
            default:
                log.error("开仓类型{}不正确", op.getValue());
                break;
        }
        return send;
    }
}
