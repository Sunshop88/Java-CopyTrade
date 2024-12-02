package net.maku.subcontrol.trader.strategy;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.*;
import net.maku.followcom.enums.*;
import net.maku.followcom.pojo.EaOrderInfo;
import net.maku.framework.common.config.JacksonConfig;
import net.maku.framework.common.utils.ThreadPoolUtils;
import net.maku.subcontrol.entity.FollowSubscribeOrderEntity;
import net.maku.subcontrol.rule.AbstractFollowRule;
import net.maku.subcontrol.trader.*;
import online.mtapi.mt4.Op;
import online.mtapi.mt4.Order;
import online.mtapi.mt4.QuoteClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;


/**
 * MT4 跟单者处理开仓信号策略
 */
@Slf4j
@Component
@AllArgsConstructor
public class OrderSendCopier extends AbstractOperation implements IOperationStrategy {

    @Override
    public void operate(AbstractApiTrader trader,EaOrderInfo orderInfo, int flag) {
        log.info("请求进入时间1"+trader.getTrader().getId());
        FollowTraderEntity copier = trader.getTrader();
        orderInfo.setSlaveReceiveOpenTime(LocalDateTime.now());
        FollowTraderSubscribeEntity leaderCopier = followTraderSubscribeService.subscription(copier.getId(), orderInfo.getMasterId());
        //存入下单方式
        orderInfo.setPlaceType(leaderCopier.getPlacedType());
        //查看喊单账号信息
        FollowPlatformEntity followPlatform = followTraderService.getPlatForm(orderInfo.getMasterId());
        // 查看品种匹配 模板
        List<FollowVarietyEntity> followVarietyEntityList= followVarietyService.getListByTemplated(copier.getTemplateId());
        List<FollowVarietyEntity> collect = followVarietyEntityList.stream().filter(o ->ObjectUtil.isNotEmpty(o.getBrokerSymbol())&&o.getBrokerSymbol().equals(orderInfo.getOriSymbol())&&o.getBrokerName().equals(followPlatform.getBrokerName())).collect(Collectors.toList());
        if (ObjectUtil.isNotEmpty(collect)){
            //获得跟单账号对应品种
            FollowPlatformEntity copyPlat = followTraderService.getPlatForm(copier.getId());
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
        }else {
            //未发现品种匹配
            log.info("未发现此订单品种匹配{},品种{}",orderInfo.getTicket(),orderInfo.getOriSymbol());
        }
        if (ObjectUtil.isEmpty(orderInfo.getSymbolList())){
            try{
                //如果没有此品种匹配，校验是否可以获取报价
                if (ObjectUtil.isEmpty(trader.quoteClient.GetQuote(orderInfo.getOriSymbol()))){
                    //订阅
                    trader.quoteClient.Subscribe(orderInfo.getOriSymbol());
                }
                trader.quoteClient.GetQuote(orderInfo.getOriSymbol());
            } catch (Exception e) {
                log.info("品种异常,不可下单{}+++++++账号{}" , orderInfo.getOriSymbol(),copier.getId());
            }
            orderInfo.setSymbolList(Collections.singletonList(orderInfo.getOriSymbol()));
        }
        FollowSubscribeOrderEntity openOrderMapping = new FollowSubscribeOrderEntity(orderInfo, copier);
        //  依次对备选品种进行开仓尝试
        for (String symbol : orderInfo.getSymbolList()) {
            orderInfo.setSymbol(symbol);
            AbstractFollowRule.PermitInfo permitInfo = this.followRule.permit(leaderCopier, orderInfo, trader);
            openOrderMapping.setSlaveSymbol(orderInfo.getSymbol());
            openOrderMapping.setLeaderCopier(leaderCopier);
            openOrderMapping.setSlaveLots(BigDecimal.valueOf(permitInfo.getLots()));
            openOrderMapping.setMasterOrSlave(TraderTypeEnum.SLAVE_REAL.getType());
            openOrderMapping.setExtra("[开仓]" + permitInfo.getExtra());
            if (sendOrder(trader,orderInfo, leaderCopier, openOrderMapping,flag)) {
                break;
            }
            followSubscribeOrderService.saveOrUpdate(openOrderMapping, Wrappers.<FollowSubscribeOrderEntity>lambdaUpdate().eq(FollowSubscribeOrderEntity::getMasterId, openOrderMapping.getMasterId()).eq(FollowSubscribeOrderEntity::getMasterTicket, openOrderMapping.getMasterTicket()).eq(FollowSubscribeOrderEntity::getSlaveId, openOrderMapping.getSlaveId()));
        }
    }

    public boolean sendOrder(AbstractApiTrader trader, EaOrderInfo orderInfo, FollowTraderSubscribeEntity leaderCopier,
                             FollowSubscribeOrderEntity openOrderMapping, Integer flag) {

        ThreadPoolUtils.getScheduledExecutorSend().submit(() -> {
            try {
                log.info("请求进入时间开始:"+trader.getTrader().getId());

                QuoteClient quoteClient=trader.quoteClient;
                FollowTraderEntity followTraderEntity=followTraderService.getById(Long.valueOf(trader.getTrader().getId()));
                if (ObjectUtil.isEmpty(trader) || ObjectUtil.isEmpty(trader.quoteClient)
                        || !trader.quoteClient.Connected()) {
                    quoteClient = followPlatformService.tologin(followTraderEntity);
                    if (ObjectUtil.isEmpty(quoteClient)) {
                        throw new RuntimeException("登录异常"+trader.getTrader().getId());
                    }
                }
                double asksub = quoteClient.GetQuote(orderInfo.getSymbol()).Ask;
                double bidsub = quoteClient.GetQuote(orderInfo.getSymbol()).Bid;
                LocalDateTime startTime = LocalDateTime.now();
                double startPrice = trader.getTrader().getType().equals(Op.Buy.getValue())?asksub:bidsub;
                Order order = quoteClient.OrderClient.OrderSend(
                            orderInfo.getSymbol(), op(orderInfo, leaderCopier),
                            openOrderMapping.getSlaveLots().doubleValue(),
                            trader.getTrader().getType().equals(Op.Buy.getValue())?asksub:bidsub, Integer.MAX_VALUE, BigDecimal.ZERO.doubleValue(),
                            BigDecimal.ZERO.doubleValue(), comment(orderInfo).toUpperCase(),
                            Integer.parseInt(orderInfo.getAccount().trim()), null
                    );
                LocalDateTime endTime = LocalDateTime.now();
                log.info("下单详情 账号:"+trader.getTrader().getId()+"平台:"+trader.getTrader().getPlatform()+"节点:"+quoteClient.Host+":"+quoteClient.Port);
                log.info("请求进入时间结束:"+trader.getTrader().getId());
                OrderResultEvent event = new OrderResultEvent(order, orderInfo, openOrderMapping, trader.getTrader(), flag,startTime,endTime,startPrice);
                ObjectMapper mapper = JacksonConfig.getObjectMapper();
                String jsonEvent = mapper.writeValueAsString(event);
                // 保存到批量发送队列
                kafkaMessages.add(jsonEvent);
            } catch (Exception e) {
                openOrderMapping.setSlaveType(Op.forValue(orderInfo.getType()).getValue());
                openOrderMapping.setSlaveTicket(null);
                openOrderMapping.setFlag(CopyTradeFlag.OF1);
                openOrderMapping.setExtra("[开仓]即时价格开仓失败" + e.getMessage());
                followSubscribeOrderService.saveOrUpdate(openOrderMapping, Wrappers.<FollowSubscribeOrderEntity>lambdaUpdate()
                        .eq(FollowSubscribeOrderEntity::getMasterId, openOrderMapping.getMasterId())
                        .eq(FollowSubscribeOrderEntity::getMasterTicket, openOrderMapping.getMasterTicket())
                        .eq(FollowSubscribeOrderEntity::getSlaveId, openOrderMapping.getSlaveId()));
                log.error("OrderSend 异常", e);
            }
        });

        return true;
    }
}
