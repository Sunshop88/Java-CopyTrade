package net.maku.subcontrol.callable;

import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.*;
import net.maku.followcom.enums.CopyTradeFlag;
import net.maku.followcom.enums.TraderLogEnum;
import net.maku.followcom.enums.TraderLogTypeEnum;
import net.maku.followcom.pojo.EaOrderInfo;
import net.maku.followcom.service.*;
import net.maku.followcom.util.FollowConstant;
import net.maku.framework.common.cache.RedisUtil;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.security.user.SecurityUser;
import net.maku.subcontrol.entity.FollowSubscribeOrderEntity;
import net.maku.subcontrol.pojo.CachedCopierOrderInfo;
import net.maku.subcontrol.service.FollowSubscribeOrderService;
import net.maku.subcontrol.trader.OrderResultEvent;
import online.mtapi.mt4.Op;
import online.mtapi.mt4.Order;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@AllArgsConstructor
public class KafkaMessageConsumer {
    private final FollowSubscribeOrderService openOrderMappingService;
    private final FollowTraderService followTraderService;
    private final FollowOrderDetailService followOrderDetailService;
    private final RedisUtil redisUtil;
    private final FollowVpsService followVpsService;
    private final FollowTraderLogService followTraderLogService;
    private final FollowSysmbolSpecificationService followSysmbolSpecificationService;

    @KafkaListener(topics = "order-send", containerFactory = "kafkaListenerContainerFactory")
    public void consumeMessageMasterSend(List<String> messages, Acknowledgment acknowledgment) {
        messages.parallelStream().forEach(message -> {
            OrderResultEvent orderResultEvent = JSON.parseObject(message, OrderResultEvent.class);
            log.info("kafka消费"+orderResultEvent);
            if (ObjectUtil.isNotEmpty(orderResultEvent.getCopier().getIpAddr())&&orderResultEvent.getCopier().getIpAddr().equals(FollowConstant.LOCAL_HOST)){
                try {
                    handleOrderResult(
                            orderResultEvent.getOrder(),
                            orderResultEvent.getOrderInfo(),
                            orderResultEvent.getOpenOrderMapping(),
                            orderResultEvent.getCopier(),
                            orderResultEvent.getFlag(),
                            orderResultEvent.getStartTime(),
                            orderResultEvent.getEndTime(),
                            orderResultEvent.getStartPrice(),
                            orderResultEvent.getIpAddress()
                    );
                }catch (Exception e){
                    log.info("消费异常");
                }

            }
        });
        acknowledgment.acknowledge(); // 全部处理完成后提交偏移量
    }

//    @KafkaListener(topics = "order-close", groupId = "order-group", containerFactory = "kafkaListenerContainerFactory")
//    public void consumeMessageMasterClose(List<String> messages, Acknowledgment acknowledgment) {
//        messages.forEach(message -> {
//
//        });
//        acknowledgment.acknowledge(); // 全部处理完成后提交偏移量
//    }


    private void handleOrderResult(Order order, EaOrderInfo orderInfo,
                                   FollowSubscribeOrderEntity openOrderMapping, FollowTraderEntity copier, Integer flag, LocalDateTime startTime, LocalDateTime endTime,double price,String ip) {
        // 处理下单成功结果，记录日志和缓存
        log.info("[MT4跟单者:{}] 下单成功, 订单: {}", copier.getAccount(), order);
        openOrderMapping.setCopierOrder(order, orderInfo);
        openOrderMapping.setFlag(CopyTradeFlag.OS);
        openOrderMapping.setExtra("[开仓]即时价格成交");

        // 数据持久化操作
        persistOrderMapping(openOrderMapping, order, orderInfo, copier, startTime, endTime,price,ip);

        // 缓存跟单数据
        cacheCopierOrder(orderInfo, order,openOrderMapping);

        // 日志记录
        logFollowOrder(copier, orderInfo, openOrderMapping, flag,ip);


    }


//    private void handleOrderFailure(EaOrderInfo orderInfo, FollowSubscribeOrderEntity openOrderMapping, Exception e) {
//        openOrderMapping.setSlaveType(Op.forValue(orderInfo.getType()).getValue());
//        openOrderMapping.setSlaveTicket(null);
//        openOrderMapping.setFlag(CopyTradeFlag.OF1);
//        openOrderMapping.setExtra("[开仓]即时价格开仓失败" + e.getMessage());
//
//        // 数据持久化失败状态
//        persistOrderMapping(openOrderMapping);
//    }

    private void persistOrderMapping(FollowSubscribeOrderEntity openOrderMapping, Order order, EaOrderInfo orderInfo, FollowTraderEntity trader, LocalDateTime startTime, LocalDateTime endTime,double price,String ip) {
        openOrderMappingService.saveOrUpdate(openOrderMapping, Wrappers.<FollowSubscribeOrderEntity>lambdaUpdate()
                .eq(FollowSubscribeOrderEntity::getMasterId, openOrderMapping.getMasterId())
                .eq(FollowSubscribeOrderEntity::getMasterTicket, openOrderMapping.getMasterTicket())
                .eq(FollowSubscribeOrderEntity::getSlaveId, openOrderMapping.getSlaveId()));
        FollowPlatformEntity platForm = followTraderService.getPlatForm(trader.getId());
        log.info("记录详情"+trader.getId()+"订单"+order.Ticket);
        FollowOrderDetailEntity followOrderDetailEntity = new FollowOrderDetailEntity();
        followOrderDetailEntity.setRequestOpenPrice(BigDecimal.valueOf(price));
        followOrderDetailEntity.setTraderId(trader.getId());
        followOrderDetailEntity.setAccount(trader.getAccount());
        followOrderDetailEntity.setSymbol(orderInfo.getSymbol());
        followOrderDetailEntity.setCreator(SecurityUser.getUserId());
        followOrderDetailEntity.setCreateTime(LocalDateTime.now());
        followOrderDetailEntity.setSendNo("11111");
        followOrderDetailEntity.setType(orderInfo.getType());
        followOrderDetailEntity.setPlacedType(orderInfo.getPlaceType());
        followOrderDetailEntity.setPlatform(trader.getPlatform());
        followOrderDetailEntity.setBrokeName(platForm.getBrokerName());
        followOrderDetailEntity.setIpAddr(trader.getIpAddr());
        followOrderDetailEntity.setServerName(trader.getServerName());
        followOrderDetailEntity.setCommission(BigDecimal.valueOf(order.Commission));
        followOrderDetailEntity.setOpenTime(DateUtil.toLocalDateTime(DateUtil.offsetHour(DateUtil.date(order.OpenTime), -8)));
        followOrderDetailEntity.setOpenPrice(BigDecimal.valueOf(order.OpenPrice));
        followOrderDetailEntity.setOrderNo(order.Ticket);
        followOrderDetailEntity.setRequestOpenTime(startTime);
        followOrderDetailEntity.setResponseOpenTime(endTime);
        followOrderDetailEntity.setSize(BigDecimal.valueOf(order.Lots));
        followOrderDetailEntity.setSl(BigDecimal.valueOf(order.StopLoss));
        followOrderDetailEntity.setSwap(BigDecimal.valueOf(order.Swap));
        followOrderDetailEntity.setTp(BigDecimal.valueOf(order.TakeProfit));
        followOrderDetailEntity.setRateMargin(order.RateMargin);
        followOrderDetailEntity.setMagical(orderInfo.getTicket());
        followOrderDetailEntity.setSourceUser(orderInfo.getAccount());
        followOrderDetailEntity.setServerHost(ip);
        followOrderDetailService.save(followOrderDetailEntity);
        //滑点分析
        updateSendOrder(trader.getId(),order.Ticket);
    }

    private void cacheCopierOrder(EaOrderInfo orderInfo, Order order,FollowSubscribeOrderEntity openOrderMapping) {
        CachedCopierOrderInfo cachedOrderInfo = new CachedCopierOrderInfo(order);
        String mapKey = orderInfo.getSlaveId() + "#" + openOrderMapping.getSlaveAccount();
        redisUtil.hset(Constant.FOLLOW_SUB_ORDER + mapKey, Long.toString(orderInfo.getTicket()), cachedOrderInfo, 0);
    }

    private void logFollowOrder(FollowTraderEntity copier, EaOrderInfo orderInfo, FollowSubscribeOrderEntity openOrderMapping, Integer flag,String ip) {
        FollowTraderLogEntity logEntity = new FollowTraderLogEntity();
        FollowVpsEntity followVpsEntity = followVpsService.getById(copier.getServerId());
        logEntity.setVpsClient(followVpsEntity.getClientId());
        logEntity.setVpsId(copier.getServerId());
        logEntity.setVpsName(copier.getServerName());
        logEntity.setTraderType(TraderLogEnum.FOLLOW_OPERATION.getType());
        logEntity.setCreateTime(LocalDateTime.now());
        logEntity.setType(flag == 0 ? TraderLogTypeEnum.SEND.getType() : TraderLogTypeEnum.REPAIR.getType());
        String remark = (flag == 0 ? FollowConstant.FOLLOW_SEND : FollowConstant.FOLLOW_REPAIR_SEND)
                + ", 策略账号=" + orderInfo.getAccount()
                + ", 单号=" + orderInfo.getTicket()
                + ", 跟单账号=" + openOrderMapping.getSlaveAccount()
                + ", 品种=" + openOrderMapping.getSlaveSymbol()
                + ", 手数=" + openOrderMapping.getSlaveLots()
                + ", 类型=" + Op.forValue(openOrderMapping.getSlaveType()).name()
                + ", 节点=" + ip
                ;
        logEntity.setLogDetail(remark);
        followTraderLogService.save(logEntity);
    }


    private void updateSendOrder(long traderId, Integer orderNo) {
        //获取symbol信息
        Map<String, FollowSysmbolSpecificationEntity> specificationEntityMap = getSymbolSpecification(traderId);
        //查看下单所有数据
        List<FollowOrderDetailEntity> list = followOrderDetailService.list(new LambdaQueryWrapper<FollowOrderDetailEntity>().eq(FollowOrderDetailEntity::getOrderNo, orderNo));
        //删除缓存
        redisUtil.del(Constant.TRADER_ORDER + traderId);
        //进行滑点分析
        list.stream().filter(o -> ObjectUtil.isNotEmpty(o.getOpenTime())).collect(Collectors.toList()).parallelStream().forEach(o -> {
            FollowSysmbolSpecificationEntity followSysmbolSpecificationEntity = specificationEntityMap.get(o.getSymbol());
            BigDecimal hd;
             //增加一下判空
            if (ObjectUtil.isNotEmpty(followSysmbolSpecificationEntity) && followSysmbolSpecificationEntity.getProfitMode().equals("Forex")) {
                //如果forex 并包含JPY 也是100
                if (o.getSymbol().contains("JPY")) {
                    hd = new BigDecimal("100");
                } else {
                    hd = new BigDecimal("10000");
                }
            } else {
                //如果非forex 都是 100
                hd = new BigDecimal("100");
            }
            long seconds = DateUtil.between(DateUtil.date(o.getResponseOpenTime()), DateUtil.date(o.getRequestOpenTime()), DateUnit.MS);
            o.setOpenTimeDifference((int) seconds);
            o.setOpenPriceSlip(o.getOpenPrice().subtract(o.getRequestOpenPrice()).multiply(hd).abs());
            followOrderDetailService.updateById(o);
        });
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