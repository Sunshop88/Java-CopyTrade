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
import net.maku.followcom.enums.*;
import net.maku.followcom.pojo.EaOrderInfo;
import net.maku.followcom.service.*;
import net.maku.followcom.util.FollowConstant;
import net.maku.framework.common.cache.RedisUtil;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.utils.ThreadPoolUtils;
import net.maku.framework.security.user.SecurityUser;
import net.maku.subcontrol.entity.FollowOrderHistoryEntity;
import net.maku.subcontrol.entity.FollowSubscribeOrderEntity;
import net.maku.subcontrol.pojo.CachedCopierOrderInfo;
import net.maku.subcontrol.service.FollowOrderHistoryService;
import net.maku.subcontrol.service.FollowSubscribeOrderService;
import net.maku.subcontrol.trader.LeaderApiTrader;
import net.maku.subcontrol.trader.LeaderApiTradersAdmin;
import net.maku.subcontrol.trader.OrderResultCloseEvent;
import net.maku.subcontrol.trader.OrderResultEvent;
import online.mtapi.mt4.Op;
import online.mtapi.mt4.Order;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@AllArgsConstructor
public class KafkaMessageConsumer {
    private final FollowSubscribeOrderService openOrderMappingService;
    private final FollowOrderDetailService followOrderDetailService;
    private final RedisUtil redisUtil;
    private final FollowVpsService followVpsService;
    private final FollowTraderLogService followTraderLogService;
    private final FollowSysmbolSpecificationService followSysmbolSpecificationService;
    private final FollowPlatformService followPlatformService;
    private final FollowOrderHistoryService followOrderHistoryService;
    private final CacheManager cacheManager;
    private final FollowTraderService followTraderService;
    private final LeaderApiTradersAdmin leaderApiTradersAdmin;
    private final FollowTraderSubscribeService followTraderSubscribeService;

    @KafkaListener(topics = "order-send", groupId = "order-group", containerFactory = "kafkaListenerContainerFactory")
    public void consumeMessageMasterSend(List<String> messages, Acknowledgment acknowledgment) {
        messages.forEach(message -> {
            ThreadPoolUtils.getExecutor().execute(()->{
                OrderResultEvent orderResultEvent = JSON.parseObject(message, OrderResultEvent.class);
                log.info("kafka消费" + orderResultEvent);
                if (ObjectUtil.isNotEmpty(orderResultEvent.getCopier().getIpAddr()) && orderResultEvent.getCopier().getIpAddr().equals(FollowConstant.LOCAL_HOST)) {
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
                    } catch (Exception e) {
                        log.info("消费异常");
                    }
                }
            });
        });
        acknowledgment.acknowledge(); // 全部处理完成后提交偏移量
    }

    @KafkaListener(topics = "order-close", groupId = "order-group", containerFactory = "kafkaListenerContainerFactory")
    public void consumeMessageMasterClose(List<String> messages, Acknowledgment acknowledgment) {
        messages.forEach(message -> {
            ThreadPoolUtils.getExecutor().execute(()->{
                OrderResultCloseEvent orderResultEvent = JSON.parseObject(message, OrderResultCloseEvent.class);
                Order order = orderResultEvent.getOrder();
                FollowTraderEntity followTraderEntity = followTraderService.getFollowById(orderResultEvent.getCopier().getId());
                EaOrderInfo orderInfo = orderResultEvent.getOrderInfo();
                Integer flag=orderResultEvent.getFlag();
                log.info("kafka消费Close"+orderResultEvent);
                log.info("OrderClose平仓{}", order.Ticket);
                openOrderMappingService.update(Wrappers.<FollowSubscribeOrderEntity>lambdaUpdate()
                        .set(FollowSubscribeOrderEntity::getFlag, CopyTradeFlag.POS)
                        .set(FollowSubscribeOrderEntity::getMasterCloseTime, orderInfo.getCloseTime())
                        .set(FollowSubscribeOrderEntity::getMasterProfit, orderResultEvent.getLeaderProfit())
                        .set(FollowSubscribeOrderEntity::getDetectedCloseTime, orderInfo.getDetectedCloseTime())
                        .set(FollowSubscribeOrderEntity::getSlavePosition, order.Lots)
                        .set(FollowSubscribeOrderEntity::getSlaveProfit, orderResultEvent.getCopierProfit())
                        .set(FollowSubscribeOrderEntity::getSlaveReceiveCloseTime, orderInfo.getSlaveReceiveCloseTime())
                        .set(FollowSubscribeOrderEntity::getSlaveCloseTime, order.CloseTime)
                        .set(FollowSubscribeOrderEntity::getExtra, "平仓成功")
                        .set(FollowSubscribeOrderEntity::getSlaveComment, order.Comment)
                        .eq(FollowSubscribeOrderEntity::getMasterId, orderInfo.getMasterId())
                        .eq(FollowSubscribeOrderEntity::getSlaveId, followTraderEntity.getId())
                        .eq(FollowSubscribeOrderEntity::getMasterTicket, orderInfo.getTicket()));
                //生成日志
                FollowTraderLogEntity followTraderLogEntity = new FollowTraderLogEntity();
                followTraderLogEntity.setTraderType(TraderLogEnum.FOLLOW_OPERATION.getType());
                FollowVpsEntity followVpsEntity = followVpsService.getById(followTraderEntity.getServerId());
                followTraderLogEntity.setVpsId(followVpsEntity.getId());
                followTraderLogEntity.setVpsClient(followVpsEntity.getClientId());
                followTraderLogEntity.setVpsName(followVpsEntity.getName());
                followTraderLogEntity.setCreateTime(LocalDateTime.now());
                followTraderLogEntity.setType(flag == 0 ? TraderLogTypeEnum.CLOSE.getType() : TraderLogTypeEnum.REPAIR.getType());
                //跟单信息
                String remark = (flag == 0 ? FollowConstant.FOLLOW_CLOSE : FollowConstant.FOLLOW_REPAIR_CLOSE) + "策略账号=" + orderInfo.getAccount() + "单号=" + orderInfo.getTicket() +
                        "跟单账号=" + followTraderEntity.getAccount() + ",单号=" + order.Ticket + ",品种=" + order.Symbol + ",手数=" + order.Lots + ",类型=" + order.Type.name()+",节点="+orderResultEvent.getIpAddress();
                followTraderLogEntity.setLogDetail(remark);
                followTraderLogEntity.setCreator(ObjectUtil.isNotEmpty(SecurityUser.getUserId())?SecurityUser.getUserId():null);
                followTraderLogService.save(followTraderLogEntity);
                //详情
                FollowOrderDetailEntity detailServiceOne = followOrderDetailService.getOne(new LambdaQueryWrapper<FollowOrderDetailEntity>().eq(FollowOrderDetailEntity::getOrderNo, order.Ticket).eq(FollowOrderDetailEntity::getTraderId,followTraderEntity.getId()).eq(FollowOrderDetailEntity::getIpAddr, FollowConstant.LOCAL_HOST));
                if (ObjectUtil.isNotEmpty(detailServiceOne)) {
                    log.info("记录详情"+detailServiceOne.getTraderId()+"订单"+detailServiceOne.getOrderNo());
                    updateCloseOrder(detailServiceOne, order, orderResultEvent.getStartTime(), orderResultEvent.getEndTime(), orderResultEvent.getStartPrice(),orderResultEvent.getIpAddress());
                }
                //删除redis中的缓存
                String mapKey = followTraderEntity.getId() + "#" + followTraderEntity.getAccount();
                redisUtil.hDel(Constant.FOLLOW_SUB_ORDER + mapKey, Long.toString(orderInfo.getTicket()));
            });
        });
        acknowledgment.acknowledge(); // 全部处理完成后提交偏移量
    }

    @KafkaListener(topics = "order-repair", groupId = "order-group", containerFactory = "kafkaListenerContainerFactory")
    public void consumeMessageMasterOrderRepair(List<String> messages, Acknowledgment acknowledgment) {
        messages.forEach(message -> {
            ThreadPoolUtils.getExecutor().execute(()->{
                log.info("kafka消费order-repair" + message);
                if (ObjectUtil.isNotEmpty(message)) {
                    try {
                        LeaderApiTrader leaderApiTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(message);
                        if (ObjectUtil.isNotEmpty(leaderApiTrader)){
                            Order[] orders = leaderApiTrader.quoteClient.GetOpenedOrders();
                            log.info("orders数量"+orders.length);
                            //查看跟单账号
                            List<FollowTraderSubscribeEntity> subscribeOrder = followTraderSubscribeService.getSubscribeOrder(Long.valueOf(message));
                            if (ObjectUtil.isNotEmpty(subscribeOrder)){
                                subscribeOrder.forEach(o->{
                                    ThreadPoolUtils.getExecutor().execute(()->{
                                        String mapKey = o.getSlaveId() + "#" + o.getSlaveAccount();
                                        FollowTraderEntity slaveTrader = followTraderService.getFollowById(o.getSlaveId());
                                        Arrays.stream(orders).forEach(order->{
                                            ThreadPoolUtils.getExecutor().execute(()->{
                                                //查看是否存在跟单
                                                Object followOrder = redisUtil.hGet(Constant.FOLLOW_SUB_ORDER + mapKey, String.valueOf(order.Ticket));
                                                if (ObjectUtil.isEmpty(followOrder)){
                                                    //漏单记录为空 -添加漏单
                                                    if (ObjectUtil.isEmpty(redisUtil.hGet(Constant.FOLLOW_REPAIR_SEND + FollowConstant.LOCAL_HOST + "#"+slaveTrader.getPlatform()+"#"+leaderApiTrader.getTrader().getPlatform()+"#"+o.getSlaveAccount() + "#" + leaderApiTrader.getTrader().getAccount(),String.valueOf(order.Ticket))))
                                                    {
                                                        EaOrderInfo eaOrderInfo = send2Copiers(OrderChangeTypeEnum.NEW, order, 0, leaderApiTrader.quoteClient.Account().currency, LocalDateTime.now(),leaderApiTrader.getTrader());
                                                        redisUtil.hSet(Constant.FOLLOW_REPAIR_SEND + FollowConstant.LOCAL_HOST + "#"+slaveTrader.getPlatform()+"#"+leaderApiTrader.getTrader().getPlatform()+"#"+o.getSlaveAccount() + "#" + leaderApiTrader.getTrader().getAccount(),String.valueOf(order.Ticket),eaOrderInfo);
                                                    }
                                                }
                                            });
                                        });
                                    });
                                });
                            }
                        }else {
                            log.error("order-repair 该账户未登录"+message);
                        }
                    } catch (Exception e) {
                        log.error("消费异常");
                    }
                }
            });
        });
        acknowledgment.acknowledge(); // 全部处理完成后提交偏移量
    }

    protected EaOrderInfo send2Copiers(OrderChangeTypeEnum type, online.mtapi.mt4.Order order, double equity, String currency, LocalDateTime detectedDate,FollowTraderEntity leader) {

        // 并且要给EaOrderInfo添加额外的信息：喊单者id+喊单者账号+喊单者服务器
        // #84 喊单者发送订单前需要处理前后缀
        EaOrderInfo orderInfo = new EaOrderInfo(order, leader.getId() ,leader.getAccount(), leader.getServerName(), equity, currency, Boolean.FALSE);
        assembleOrderInfo(type, orderInfo, detectedDate);
        return orderInfo;
    }

    void assembleOrderInfo(OrderChangeTypeEnum type, EaOrderInfo orderInfo, LocalDateTime detectedDate) {
        if (type == OrderChangeTypeEnum.NEW) {
            orderInfo.setOriginal(AcEnum.MO);
            orderInfo.setDetectedOpenTime(detectedDate);
        } else if (type == OrderChangeTypeEnum.CLOSED) {
            orderInfo.setDetectedCloseTime(detectedDate);
            orderInfo.setOriginal(AcEnum.MC);
        } else if (type == OrderChangeTypeEnum.MODIFIED) {
            orderInfo.setOriginal(AcEnum.MM);
        }
    }

    private void updateCloseOrder(FollowOrderDetailEntity followOrderDetailEntity, Order order, LocalDateTime startTime, LocalDateTime endTime, double price,String ipaddr) {
        //保存平仓信息
        followOrderDetailEntity.setCloseTimeDifference((int)order.sendTimeDifference);
        followOrderDetailEntity.setRequestCloseTime(startTime);
        followOrderDetailEntity.setResponseCloseTime(endTime);
        followOrderDetailEntity.setCloseTime(order.CloseTime);
        followOrderDetailEntity.setClosePrice(BigDecimal.valueOf(order.ClosePrice));
        followOrderDetailEntity.setRequestClosePrice(new BigDecimal(price));
        followOrderDetailEntity.setSwap(BigDecimal.valueOf(order.Swap));
        followOrderDetailEntity.setCommission(BigDecimal.valueOf(order.Commission));
        followOrderDetailEntity.setProfit(BigDecimal.valueOf(order.Profit));
        followOrderDetailEntity.setCloseStatus(CloseOrOpenEnum.OPEN.getValue());
        FollowVpsEntity vps = followVpsService.getVps(FollowConstant.LOCAL_HOST);
        followOrderDetailEntity.setCloseServerName(vps.getName());
        followOrderDetailEntity.setCloseServerHost(ipaddr);
        followOrderDetailEntity.setCloseIpAddr(FollowConstant.LOCAL_HOST);
        followOrderDetailEntity.setCloseId(0);
        //获取symbol信息
        Map<String, FollowSysmbolSpecificationEntity> specificationEntityMap = followSysmbolSpecificationService.getByTraderId(followOrderDetailEntity.getTraderId());
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
        followOrderDetailEntity.setClosePriceSlip(followOrderDetailEntity.getClosePrice().subtract(followOrderDetailEntity.getRequestClosePrice()).multiply(hd).abs());
        followOrderDetailService.updateById(followOrderDetailEntity);
    }

    private void handleOrderResult(Order order, EaOrderInfo orderInfo,
                                   FollowSubscribeOrderEntity openOrderMapping, FollowTraderEntity copier, Integer flag, LocalDateTime startTime, LocalDateTime endTime,double price,String ip) {
        copier=followTraderService.getFollowById(copier.getId());
        // 处理下单成功结果，记录日志和缓存
        log.info("[MT4跟单者:{}] 下单成功, 订单: {}", copier.getAccount(), order);

        // 数据持久化操作
        persistOrderMapping(openOrderMapping, order, orderInfo, copier, startTime, endTime,price,ip);

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
        FollowPlatformEntity platForm = followPlatformService.getPlatFormById(trader.getPlatformId().toString());
        log.info("记录详情"+trader.getId()+"订单"+order.Ticket);
        FollowOrderDetailEntity followOrderDetailEntity = new FollowOrderDetailEntity();
        followOrderDetailEntity.setOpenTimeDifference((int)order.sendTimeDifference);
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
        logEntity.setCreator(ObjectUtil.isNotEmpty(SecurityUser.getUserId())?SecurityUser.getUserId():null);
        followTraderLogService.save(logEntity);
    }


    private void updateSendOrder(long traderId, Integer orderNo) {
        //获取symbol信息
        Map<String, FollowSysmbolSpecificationEntity> specificationEntityMap = followSysmbolSpecificationService.getByTraderId(traderId);
        //查看下单所有数据
        List<FollowOrderDetailEntity> list = followOrderDetailService.list(new LambdaQueryWrapper<FollowOrderDetailEntity>().eq(FollowOrderDetailEntity::getOrderNo, orderNo));
        //进行滑点分析
        list.stream().filter(o -> ObjectUtil.isNotEmpty(o.getOpenTime())).collect(Collectors.toList()).forEach(o -> {
            ThreadPoolUtils.getExecutor().execute(()->{
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
                o.setOpenPriceSlip(o.getOpenPrice().subtract(o.getRequestOpenPrice()).multiply(hd).abs());
                followOrderDetailService.updateById(o);
            });
        });
    }
}