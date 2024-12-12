package net.maku.subcontrol.trader.strategy;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
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
import online.mtapi.mt4.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;


/**
 * MT4 跟单者处理开仓信号策略
 */
@Slf4j
@Component
@AllArgsConstructor
public class OrderSendCopier extends AbstractOperation implements IOperationStrategy {
    private final CopierApiTradersAdmin copierApiTradersAdmin;


    @Override
    public void operate(AbstractApiTrader trader,EaOrderInfo orderInfo, int flag) {
        log.info(":请求进入时间1"+trader.getTrader().getId());
        FollowTraderEntity copier = trader.getTrader();
        orderInfo.setSlaveReceiveOpenTime(LocalDateTime.now());
        FollowTraderSubscribeEntity leaderCopier = followTraderSubscribeService.subscription(copier.getId(), orderInfo.getMasterId());
        //存入下单方式
        orderInfo.setPlaceType(leaderCopier.getPlacedType());
        log.info("请求进入时间1.0:"+trader.getTrader().getId());
        //查看喊单账号信息
        FollowTraderEntity followTraderEntity = followTraderService.getFollowById(orderInfo.getMasterId());
        FollowPlatformEntity followPlatform = followPlatformService.getPlatFormById(followTraderEntity.getPlatformId().toString());
        // 查看品种匹配 模板
        log.info("请求进入时间1.1:"+trader.getTrader().getId());
        List<FollowVarietyEntity> followVarietyEntityList= followVarietyService.getListByTemplated(copier.getTemplateId());
        log.info("请求进入时间2:"+trader.getTrader().getId());

        List<FollowVarietyEntity> collect = followVarietyEntityList.stream().filter(o ->ObjectUtil.isNotEmpty(o.getBrokerName())&&ObjectUtil.isNotEmpty(o.getBrokerSymbol())&&o.getBrokerSymbol().equals(orderInfo.getOriSymbol())&&o.getBrokerName().equals(followPlatform.getBrokerName())).collect(Collectors.toList());
        if (ObjectUtil.isNotEmpty(collect)){
            //获得跟单账号对应品种
            FollowPlatformEntity copyPlat = followPlatformService.getPlatFormById(copier.getPlatformId().toString());
            List<FollowVarietyEntity> collectCopy = followVarietyEntityList.stream().filter(o -> ObjectUtil.isNotEmpty(o.getBrokerName())&&o.getStdSymbol().equals(collect.get(0).getStdSymbol()) && o.getBrokerName().equals(copyPlat.getBrokerName())).collect(Collectors.toList());
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
            } catch (Exception e) {
                log.info("品种异常,不可下单{}+++++++账号{}" , orderInfo.getOriSymbol(),copier.getId());
            }
            orderInfo.setSymbolList(Collections.singletonList(orderInfo.getOriSymbol()));
        }
        log.info("请求进入时间3:"+trader.getTrader().getId());
        FollowSubscribeOrderEntity openOrderMapping = new FollowSubscribeOrderEntity(orderInfo, copier);
        //  依次对备选品种进行开仓尝试
        for (String symbol : orderInfo.getSymbolList()) {
            log.info("请求进入时间3.1:"+trader.getTrader().getId());
            orderInfo.setSymbol(symbol);
            AbstractFollowRule.PermitInfo permitInfo = this.followRule.permit(leaderCopier, orderInfo, trader);
            openOrderMapping.setSlaveSymbol(orderInfo.getSymbol());
            openOrderMapping.setLeaderCopier(leaderCopier);
            openOrderMapping.setSlaveLots(BigDecimal.valueOf(permitInfo.getLots()));
            openOrderMapping.setMasterOrSlave(TraderTypeEnum.SLAVE_REAL.getType());
            openOrderMapping.setExtra("[开仓]" + permitInfo.getExtra());
            log.info("请求进入时间3.2:"+trader.getTrader().getId());
            if (sendOrder(trader,orderInfo, leaderCopier, openOrderMapping,flag)) {
                break;
            }
            followSubscribeOrderService.saveOrUpdate(openOrderMapping, Wrappers.<FollowSubscribeOrderEntity>lambdaUpdate().eq(FollowSubscribeOrderEntity::getMasterId, openOrderMapping.getMasterId()).eq(FollowSubscribeOrderEntity::getMasterTicket, openOrderMapping.getMasterTicket()).eq(FollowSubscribeOrderEntity::getSlaveId, openOrderMapping.getSlaveId()));
        }
    }

    public boolean sendOrder(AbstractApiTrader trader, EaOrderInfo orderInfo, FollowTraderSubscribeEntity leaderCopier,
                             FollowSubscribeOrderEntity openOrderMapping, Integer flag) {
        log.info("请求进入时间4:"+trader.getTrader().getId());
        CompletableFuture.runAsync(() -> {
            try {
                FollowTraderEntity followTraderEntity =followTraderService.getFollowById(Long.valueOf(trader.getTrader().getId()));
                QuoteClient quoteClient = trader.quoteClient;
                log.info("请求进入时间开始: " + trader.getTrader().getId());
                if (ObjectUtil.isEmpty(trader) || ObjectUtil.isEmpty(trader.quoteClient) || !trader.quoteClient.Connected()) {
                    copierApiTradersAdmin.removeTrader(trader.getTrader().getId().toString());
                    ConCodeEnum conCodeEnum = copierApiTradersAdmin.addTrader(followTraderEntity);
                    if (conCodeEnum == ConCodeEnum.SUCCESS) {
                        quoteClient = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(followTraderEntity.getId().toString()).quoteClient;
                        CopierApiTrader copierApiTrader1 = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(followTraderEntity.getId().toString());
                        copierApiTrader1.setTrader(followTraderEntity);
                    } else {
                        throw new RuntimeException("登录异常" + trader.getTrader().getId());
                    }
                }

                if (ObjectUtil.isEmpty(quoteClient.GetQuote(orderInfo.getSymbol()))){
                    //订阅
                    quoteClient.Subscribe(orderInfo.getSymbol());
                }
                double bidsub =0;
                double asksub =0;
                QuoteEventArgs quoteEventArgs = null;
                while (quoteEventArgs==null && quoteClient.Connected()) {
                    Thread.sleep(50);
                    quoteEventArgs=quoteClient.GetQuote(orderInfo.getSymbol());
                    bidsub =quoteEventArgs.Bid;
                    asksub =quoteEventArgs.Ask;
                }
                log.info("下单详情 账号: " + followTraderEntity.getId() + " 品种: " + orderInfo.getSymbol() + " 手数: " + openOrderMapping.getSlaveLots());

                // 执行订单发送
                double startPrice=followTraderEntity.getType().equals(Op.Buy.getValue()) ? asksub : bidsub;
                // 记录开始时间
                LocalDateTime startTime=LocalDateTime.now();
                Order order = quoteClient.OrderClient.OrderSend(
                        orderInfo.getSymbol(),
                        op(orderInfo, leaderCopier),
                        openOrderMapping.getSlaveLots().doubleValue(),
                        startPrice,
                        Integer.MAX_VALUE,
                        BigDecimal.ZERO.doubleValue(),
                        BigDecimal.ZERO.doubleValue(),
                        comment(orderInfo).toUpperCase(),
                        orderInfo.getTicket(),
                        null
                );
                // 记录结束时间
                LocalDateTime endTime = LocalDateTime.now();
                log.info("下单详情 账号: " + followTraderEntity.getId() + " 平台: " + followTraderEntity.getPlatform() + " 节点: " + quoteClient.Host + ":" + quoteClient.Port);
                log.info("请求进入时间结束: " + followTraderEntity.getId());

                // 创建订单结果事件
                OrderResultEvent event = new OrderResultEvent(order, orderInfo, openOrderMapping, followTraderEntity, flag, startTime, endTime, startPrice, quoteClient.Host + ":" + quoteClient.Port);
                ObjectMapper mapper = JacksonConfig.getObjectMapper();
                String jsonEvent = null;
                try {
                    jsonEvent = mapper.writeValueAsString(event);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
                // 保存到批量发送队列
                kafkaMessages.add(jsonEvent);
            } catch (Exception e) {
                log.error("OrderSend 异常", e);
                throw new RuntimeException("订单发送异常", e);
            }
        }, ThreadPoolUtils.getScheduledExecutorSend());
        return true;
    }
}
