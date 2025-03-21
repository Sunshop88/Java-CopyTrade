package net.maku.subcontrol.trader.strategy;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.*;
import net.maku.followcom.enums.*;
import net.maku.followcom.pojo.EaOrderInfo;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.vo.OrderActiveInfoVO;
import net.maku.followcom.vo.OrderRepairInfoVO;
import net.maku.framework.common.cache.RedisCache;
import net.maku.framework.common.config.JacksonConfig;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.exception.ServerException;
import net.maku.framework.common.utils.JsonUtils;
import net.maku.framework.common.utils.Result;
import net.maku.framework.common.utils.ThreadPoolUtils;
import net.maku.framework.security.user.SecurityUser;
import net.maku.subcontrol.entity.FollowSubscribeOrderEntity;
import net.maku.subcontrol.pojo.CachedCopierOrderInfo;
import net.maku.subcontrol.rule.AbstractFollowRule;
import net.maku.subcontrol.trader.*;
import net.maku.subcontrol.vo.FollowOrderRepairSocketVO;
import net.maku.subcontrol.websocket.TraderOrderRepairWebSocket;
import online.mtapi.mt4.*;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
    private final RedisCache redisCache;
    private final TraderOrderRepairWebSocket traderOrderRepairWebSocket;

    @Override
    public void operate(AbstractApiTrader trader,EaOrderInfo orderInfo, int flag) {
        log.info(":请求进入时间1"+trader.getTrader().getId()+":"+orderInfo.getMasterId());
        orderInfo.setSlaveReceiveOpenTime(LocalDateTime.now());
        FollowTraderSubscribeEntity leaderCopier = followTraderSubscribeService.subscription(trader.getTrader().getId(), orderInfo.getMasterId());
        //存入下单方式
        orderInfo.setPlaceType(leaderCopier.getPlacedType());
        log.info("请求进入时间1.0:"+trader.getTrader().getId());
        //查看喊单账号信息
        FollowTraderEntity followTraderEntity = followTraderService.getFollowById(orderInfo.getMasterId());
        FollowTraderEntity copier = followTraderService.getFollowById(trader.getTrader().getId());

        FollowPlatformEntity followPlatform = followPlatformService.getPlatFormById(followTraderEntity.getPlatformId().toString());
        // 先查询品种规格是否可以匹配
        String stdSymbol =orderInfo.getOriSymbol();
        List<FollowVarietyEntity> followVarietyEntityList= followVarietyService.getListByTemplated(followTraderEntity.getTemplateId());
        Optional<FollowVarietyEntity> first = followVarietyEntityList.stream().filter(o -> orderInfo.getOriSymbol().contains(o.getStdSymbol())).findFirst();
        FollowPlatformEntity copyPlat = followPlatformService.getPlatFormById(copier.getPlatformId().toString());
        if (first.isPresent()){
            //查找到标准品种
            stdSymbol=first.get().getStdSymbol();
            log.info("OrderSendCopier 主账号 标准品种"+stdSymbol);
        }else {
            // 查看品种匹配 模板
            List<FollowVarietyEntity> collect = followVarietyEntityList.stream().filter(o ->ObjectUtil.isNotEmpty(o.getBrokerName())&&ObjectUtil.isNotEmpty(o.getBrokerSymbol())&&o.getBrokerSymbol().equals(orderInfo.getOriSymbol())&&o.getBrokerName().equals(followPlatform.getBrokerName())).collect(Collectors.toList());
            log.info("collect"+collect);
            if (ObjectUtil.isNotEmpty(collect)) {
                stdSymbol = collect.get(0).getStdSymbol();
            }else {
                log.info("未发现此订单品种匹配{},品种{}",orderInfo.getTicket(),orderInfo.getOriSymbol());
                //没有标准品种 报错
                setOrderDetail(copier,orderInfo,stdSymbol,copyPlat);
                return;
            }
        }
        //获得跟单账号对应品种
        String finalStdSymbol = stdSymbol;
        List<String> symbolList = orderInfo.getSymbolList();
        //查询品种规格数据
        List<FollowSysmbolSpecificationEntity> sysmbolSpecificationEntity = followSysmbolSpecificationService.getByTraderId(copier.getId()).stream().filter(o ->o.getSymbol().contains(finalStdSymbol)).toList();
        if (ObjectUtil.isNotEmpty(sysmbolSpecificationEntity)){
            sysmbolSpecificationEntity.forEach(o->{
                        try{
                            //如果没有此品种匹配，校验是否可以获取报价
                            if (ObjectUtil.isEmpty(trader.quoteClient.GetQuote(o.getSymbol()))){
                                //订阅
                                trader.quoteClient.Subscribe(o.getSymbol());
                            }
                            symbolList.add(o.getSymbol());
                        } catch (Exception e) {
                            log.info("品种规格异常,不可下单{}+++++++账号{}" , o.getSymbol(),copier.getId());
                        }
                    }
            );
        }else {
            // 查看品种匹配 模板
            List<FollowVarietyEntity> followVarietyEntityListCopier= followVarietyService.getListByTemplated(copier.getTemplateId());
            List<FollowVarietyEntity> collectCopy = followVarietyEntityListCopier.stream().filter(o -> ObjectUtil.isNotEmpty(o.getBrokerName())&&o.getStdSymbol().equals(finalStdSymbol) && o.getBrokerName().equals(copyPlat.getBrokerName())).collect(Collectors.toList());
            log.info("跟单品种匹配"+collectCopy+"服务商:"+copyPlat.getBrokerName()+"模板:"+copier.getTemplateId()+"类型:"+finalStdSymbol);
            collectCopy.forEach(o-> {
                if(ObjectUtil.isNotEmpty(o.getBrokerSymbol())){
                    //校验品种是否可以获取报价
                    try{
                        //如果没有此品种匹配，校验是否可以获取报价
                        if (ObjectUtil.isEmpty(trader.quoteClient.GetQuote(o.getBrokerSymbol()))){
                            //订阅
                            trader.quoteClient.Subscribe(o.getBrokerSymbol());
                        }
                        symbolList.add(o.getBrokerSymbol());
                    } catch (Exception e) {
                        log.info("品种异常,不可下单{}+++++++账号{}" , o.getBrokerSymbol(),copier.getId());
                    }
                }
            });
        }

        if (ObjectUtil.isEmpty(orderInfo.getSymbolList())){
            try{
                //如果没有此品种匹配，校验是否可以获取报价
                if (ObjectUtil.isEmpty(trader.quoteClient.GetQuote(finalStdSymbol))){
                    //订阅
                    trader.quoteClient.Subscribe(finalStdSymbol);
                }
            } catch (Exception e) {
                log.info("品种异常,不可下单{}+++++++账号{}" , finalStdSymbol,copier.getId());
            }
            orderInfo.setSymbolList(Collections.singletonList(finalStdSymbol));
        }
        log.info("请求进入时间3:"+trader.getTrader().getId());
        //  依次对备选品种进行开仓尝试
        log.info("跟单品种所有"+orderInfo.getSymbolList());//
        if (orderInfo.getSymbolList().size()>1){
            for (String symbol : orderInfo.getSymbolList()) {
                EaOrderInfo eaOrderInfo = new EaOrderInfo();
                BeanUtil.copyProperties(orderInfo,eaOrderInfo);
                eaOrderInfo.setSymbol(symbol);
                FollowSubscribeOrderEntity openOrderMapping = new FollowSubscribeOrderEntity(eaOrderInfo, copier);
                AbstractFollowRule.PermitInfo permitInfo = this.followRule.permit(leaderCopier, eaOrderInfo, trader);
                openOrderMapping.setSlaveSymbol(eaOrderInfo.getSymbol());
                openOrderMapping.setLeaderCopier(leaderCopier);
                openOrderMapping.setSlaveLots(BigDecimal.valueOf(permitInfo.getLots()));
                openOrderMapping.setMasterOrSlave(TraderTypeEnum.SLAVE_REAL.getType());
                openOrderMapping.setExtra("[开仓]" + permitInfo.getExtra());
                if (sendOrder(trader,eaOrderInfo, leaderCopier, openOrderMapping,flag,copyPlat.getBrokerName())){
                    break;
                }
            }
        }else {
            orderInfo.setSymbol(orderInfo.getSymbolList().get(0));
            FollowSubscribeOrderEntity openOrderMapping = new FollowSubscribeOrderEntity(orderInfo, copier);
            AbstractFollowRule.PermitInfo permitInfo = this.followRule.permit(leaderCopier, orderInfo, trader);
            openOrderMapping.setSlaveSymbol(orderInfo.getSymbol());
            openOrderMapping.setLeaderCopier(leaderCopier);
            openOrderMapping.setSlaveLots(BigDecimal.valueOf(permitInfo.getLots()));
            openOrderMapping.setMasterOrSlave(TraderTypeEnum.SLAVE_REAL.getType());
            openOrderMapping.setExtra("[开仓]" + permitInfo.getExtra());
            log.info("请求进入时间3.2:"+trader.getTrader().getId());
            if (followVpsService.getVps(FollowConstant.LOCAL_HOST).getIsSyn().equals(CloseOrOpenEnum.OPEN.getValue())){
                sendOrderAsy(trader,orderInfo, leaderCopier, openOrderMapping,flag,copyPlat.getBrokerName());
            }else {
                sendOrder(trader,orderInfo, leaderCopier, openOrderMapping,flag,copyPlat.getBrokerName());
            }
        }

    }

    private void setOrderDetail(FollowTraderEntity copier,EaOrderInfo orderInfo,String stdSymbol,FollowPlatformEntity copyPlat) {
        FollowOrderDetailEntity followOrderDetailEntity = new FollowOrderDetailEntity();
        followOrderDetailEntity.setTraderId(copier.getId());
        followOrderDetailEntity.setAccount(copier.getAccount());
        followOrderDetailEntity.setSymbol(stdSymbol);
        followOrderDetailEntity.setCreator(SecurityUser.getUserId());
        followOrderDetailEntity.setCreateTime(LocalDateTime.now());
        followOrderDetailEntity.setSendNo("11111");
        followOrderDetailEntity.setType(orderInfo.getType());
        followOrderDetailEntity.setPlacedType(orderInfo.getPlaceType());
        followOrderDetailEntity.setPlatform(copier.getPlatform());
        followOrderDetailEntity.setBrokeName(copyPlat.getBrokerName());
        followOrderDetailEntity.setIpAddr(copier.getIpAddr());
        followOrderDetailEntity.setServerName(copier.getServerName());
        followOrderDetailEntity.setSize(BigDecimal.ZERO);
        followOrderDetailEntity.setSourceUser(orderInfo.getAccount());
        followOrderDetailEntity.setRemark("主账号标准品种未配置");
        followOrderDetailService.save(followOrderDetailEntity);
    }

    public boolean sendOrderAsy(AbstractApiTrader trader, EaOrderInfo orderInfo, FollowTraderSubscribeEntity leaderCopier,
                             FollowSubscribeOrderEntity openOrderMapping, Integer flag,String brokeName) {
        log.info("请求进入时间4:"+trader.getTrader().getId());
        CompletableFuture.runAsync(() -> {
            FollowTraderEntity followTraderEntity =followTraderService.getFollowById(Long.valueOf(trader.getTrader().getId()));
            Op op = op(orderInfo, leaderCopier);
            String ip="";
            try {
                QuoteClient quoteClient = trader.quoteClient;
                log.info("请求进入时间开始: " + trader.getTrader().getId());
                if (ObjectUtil.isEmpty(trader) || ObjectUtil.isEmpty(trader.quoteClient) || !trader.quoteClient.Connected()) {
                    copierApiTradersAdmin.removeTrader(trader.getTrader().getId().toString());
                    ConCodeEnum conCodeEnum = copierApiTradersAdmin.addTrader(followTraderEntity);
                    if (conCodeEnum == ConCodeEnum.SUCCESS) {
                        quoteClient = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(followTraderEntity.getId().toString()).quoteClient;
                        CopierApiTrader copierApiTrader1 = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(followTraderEntity.getId().toString());
                        copierApiTrader1.setTrader(followTraderEntity);
                        copierApiTrader1.startTrade();
                    }else {
                        log.error(trader.getTrader().getId()+"掉线异常");
                        throw new RuntimeException("登录异常" + trader.getTrader().getId());
                    }
                }

                if (ObjectUtil.isEmpty(quoteClient.GetQuote(orderInfo.getSymbol()))){
                    //订阅
                    quoteClient.Subscribe(orderInfo.getSymbol());
                }
                ip=quoteClient.Host+":"+quoteClient.Port;
                double bidsub =0;
                double asksub =0;
                int loopTimes=1;
                QuoteEventArgs quoteEventArgs = null;
                while (quoteEventArgs == null && quoteClient.Connected()) {
                    quoteEventArgs = quoteClient.GetQuote(orderInfo.getSymbol());
                    if (++loopTimes > 20) {
                        break;
                    } else {
                        Thread.sleep(50);
                    }
                }
                bidsub =ObjectUtil.isNotEmpty(quoteEventArgs)?quoteEventArgs.Bid:0;
                asksub =ObjectUtil.isNotEmpty(quoteEventArgs)?quoteEventArgs.Ask:0;
                log.info("下单详情 账号: " + followTraderEntity.getId() + " 品种: " + orderInfo.getSymbol() + " 手数: " + openOrderMapping.getSlaveLots());
                Object o1 = redisCache.hGet(Constant.SYSTEM_PARAM_LOTS_MAX, Constant.LOTS_MAX);
                if(ObjectUtil.isNotEmpty(o1)){
                    String s = o1.toString().replaceAll("\"", "");
                    BigDecimal max = new BigDecimal(s.toString());
                    BigDecimal slaveLots = openOrderMapping.getSlaveLots();
                    if (slaveLots.compareTo(max)>0) {
                        throw new ServerException("超过最大手数限制");
                    }
                }

                // 执行订单发送
                double startPrice=op.equals(Op.Buy) ? asksub : bidsub;
                // 记录开始时间
                LocalDateTime startTime=LocalDateTime.now();
                long start = System.currentTimeMillis();
                OrderClient oc;
                if (ObjectUtil.isNotEmpty(quoteClient.OrderClient)) {
                    oc = quoteClient.OrderClient;
                } else {
                    oc = new OrderClient(quoteClient);
                }
                oc.PlacedType=PlacedType.forValue(leaderCopier.getPlacedType());
                Order order = oc.OrderSend(
                        orderInfo.getSymbol(),
                        op,
                        openOrderMapping.getSlaveLots().doubleValue(),
                        startPrice,
                        Integer.MAX_VALUE,
                        BigDecimal.ZERO.doubleValue(),
                        BigDecimal.ZERO.doubleValue(),
                        comment(leaderCopier,orderInfo,followTraderEntity.getServerId()),
                        orderInfo.getTicket(),
                        null
                );
                long end = System.currentTimeMillis();
                log.info("MT4下单时间差 订单:"+order.Ticket+"内部时间差:"+order.sendTimeDifference+"外部时间差:"+(end-start));
                // 记录结束时间
                LocalDateTime endTime = LocalDateTime.now();
                log.info("下单详情 账号: " + followTraderEntity.getId() + " 平台: " + followTraderEntity.getPlatform() + " 节点: " + quoteClient.Host + ":" + quoteClient.Port);
                log.info("请求进入时间结束: " + followTraderEntity.getId());
                openOrderMapping.setCopierOrder(order, orderInfo);
                openOrderMapping.setFlag(CopyTradeFlag.OS);
                openOrderMapping.setExtra("[开仓]即时价格成交");
                followSubscribeOrderService.saveOrUpdate(openOrderMapping);
                cacheCopierOrder(orderInfo, order,openOrderMapping);
                // 创建订单结果事件
                OrderResultEvent event = new OrderResultEvent(order, orderInfo, openOrderMapping, followTraderEntity, flag, startTime, endTime, startPrice, ip);
                ObjectMapper mapper = JacksonConfig.getObjectMapper();
                String jsonEvent = null;
                try {
                    jsonEvent = mapper.writeValueAsString(event);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
                // 保存到批量发送队列
                kafkaMessages.add(jsonEvent);
                if (flag==1){
                    FollowOrderRepairSocketVO followOrderRepairSocketVO = setRepairWebSocket(leaderCopier.getMasterId().toString(), leaderCopier.getSlaveId().toString(), quoteClient);
                    traderOrderRepairWebSocket.pushMessage(leaderCopier.getMasterId().toString(),leaderCopier.getSlaveId().toString(), JsonUtils.toJsonString(followOrderRepairSocketVO));
                    redisUtil.set(Constant.TRADER_TEMPORARILY_REPAIR+leaderCopier.getSlaveId(),followOrderRepairSocketVO);
                }
//                //删除漏单redis记录
//                Object o2 = redisUtil.hGetStr(Constant.REPAIR_SEND + openOrderMapping.getMasterAccount() + ":" +openOrderMapping.getMasterId(), openOrderMapping.getSlaveAccount().toString());
//                Map<Integer, OrderRepairInfoVO> repairInfoVOS = new HashMap();
//                if (o2 != null && o2.toString().trim().length() > 0) {
//                    repairInfoVOS = JSONObject.parseObject(o2.toString(), Map.class);
//                }
//               repairInfoVOS.remove(orderInfo.getTicket());
//                if(repairInfoVOS==null || repairInfoVOS.size()==0){
//                    redisUtil.hDel(Constant.REPAIR_SEND +openOrderMapping.getMasterAccount() + ":" + openOrderMapping.getMasterId(), openOrderMapping.getSlaveAccount().toString());
//                }else{
//                    redisUtil.hSetStr(Constant.REPAIR_SEND +openOrderMapping.getMasterAccount() + ":" + openOrderMapping.getMasterId(), openOrderMapping.getSlaveAccount().toString(), JSONObject.toJSONString(repairInfoVOS));
//                }
//                log.info("漏单删除,key:{},key:{},val:{},订单号:{}",Constant.REPAIR_SEND +openOrderMapping.getMasterAccount() + ":" + openOrderMapping.getMasterId(), openOrderMapping.getSlaveAccount().toString(),JSONObject.toJSONString(repairInfoVOS),orderInfo.getTicket() );
            } catch (Exception e) {
                openOrderMapping.setExtra("开仓失败"+e.getMessage());
                followSubscribeOrderService.saveOrUpdate(openOrderMapping, Wrappers.<FollowSubscribeOrderEntity>lambdaUpdate().eq(FollowSubscribeOrderEntity::getMasterId, openOrderMapping.getMasterId()).eq(FollowSubscribeOrderEntity::getMasterTicket, openOrderMapping.getMasterTicket()).eq(FollowSubscribeOrderEntity::getSlaveId, openOrderMapping.getSlaveId()));
                log.error("OrderSend 异常", e);
                FollowOrderDetailEntity followOrderDetailEntity = new FollowOrderDetailEntity();
                followOrderDetailEntity.setTraderId(followTraderEntity.getId());
                followOrderDetailEntity.setAccount(followTraderEntity.getAccount());
                followOrderDetailEntity.setSymbol(orderInfo.getSymbol());
                followOrderDetailEntity.setCreator(SecurityUser.getUserId());
                followOrderDetailEntity.setCreateTime(LocalDateTime.now());
                followOrderDetailEntity.setSendNo("11111");
                followOrderDetailEntity.setType(op.getValue());
                followOrderDetailEntity.setPlacedType(orderInfo.getPlaceType());
                followOrderDetailEntity.setPlatform(followTraderEntity.getPlatform());
                followOrderDetailEntity.setBrokeName(brokeName);
                followOrderDetailEntity.setIpAddr(followTraderEntity.getIpAddr());
                followOrderDetailEntity.setServerName(followTraderEntity.getServerName());
                followOrderDetailEntity.setSize(openOrderMapping.getSlaveLots());
                followOrderDetailEntity.setSourceUser(orderInfo.getAccount());
                followOrderDetailEntity.setServerHost(ip);
                followOrderDetailEntity.setRemark(e.getMessage());
                followOrderDetailService.save(followOrderDetailEntity);
                logFollowOrder(followTraderEntity,orderInfo,openOrderMapping,flag,ip,e.getMessage(),op);
            }
        }, ThreadPoolUtils.getExecutor());
        return true;
    }


    private void cacheCopierOrder(EaOrderInfo orderInfo, Order order,FollowSubscribeOrderEntity openOrderMapping) {
        CachedCopierOrderInfo cachedOrderInfo = new CachedCopierOrderInfo(order);
        String mapKey = openOrderMapping.getSlaveId() + "#" + openOrderMapping.getSlaveAccount();
        log.info("保存订单数据"+mapKey+"ticket"+orderInfo.getTicket());
        redisUtil.hset(Constant.FOLLOW_SUB_ORDER + mapKey, Long.toString(orderInfo.getTicket()), cachedOrderInfo, 0);
    }
    private void logFollowOrder(FollowTraderEntity copier, EaOrderInfo orderInfo, FollowSubscribeOrderEntity openOrderMapping, Integer flag,String ip,String ex,Op op) {
        FollowTraderLogEntity logEntity = new FollowTraderLogEntity();
        FollowVpsEntity followVpsEntity = followVpsService.getById(copier.getServerId());
        logEntity.setVpsClient(followVpsEntity.getClientId());
        logEntity.setVpsId(copier.getServerId());
        logEntity.setVpsName(copier.getServerName());
        logEntity.setTraderType(TraderLogEnum.FOLLOW_OPERATION.getType());
        logEntity.setCreateTime(LocalDateTime.now());
        logEntity.setStatus(CloseOrOpenEnum.CLOSE.getValue());
        logEntity.setType(flag == 0 ? TraderLogTypeEnum.SEND.getType() : TraderLogTypeEnum.REPAIR.getType());
        String remark = (flag == 0 ? FollowConstant.FOLLOW_SEND : FollowConstant.FOLLOW_REPAIR_SEND)
                + ", 【失败】策略账号=" + orderInfo.getAccount()
                + ", 单号=" + orderInfo.getTicket()
                + ", 跟单账号=" + openOrderMapping.getSlaveAccount()
                + ", 品种=" + openOrderMapping.getSlaveSymbol()
                + ", 手数=" + openOrderMapping.getSlaveLots()
                + ", 类型=" + op.name()
                + ", 节点=" + ip
                + ", 失败原因=" + ex
                ;
        logEntity.setLogDetail(remark);
        logEntity.setCreator(ObjectUtil.isNotEmpty(SecurityUser.getUserId())?SecurityUser.getUserId():null);
        followTraderLogService.save(logEntity);
    }


    public boolean sendOrder(AbstractApiTrader trader, EaOrderInfo orderInfo, FollowTraderSubscribeEntity leaderCopier,
                             FollowSubscribeOrderEntity openOrderMapping, Integer flag,String brokeName) {
        log.info("请求进入时间4:"+trader.getTrader().getId());
        FollowTraderEntity followTraderEntity =followTraderService.getFollowById(Long.valueOf(trader.getTrader().getId()));
        Op op = op(orderInfo, leaderCopier);
        String ip="";
        try {
            QuoteClient quoteClient = trader.quoteClient;
            log.info("请求进入时间开始: " + trader.getTrader().getId());
            if (ObjectUtil.isEmpty(trader) || ObjectUtil.isEmpty(trader.quoteClient) || !trader.quoteClient.Connected()) {
                copierApiTradersAdmin.removeTrader(trader.getTrader().getId().toString());
                ConCodeEnum conCodeEnum = copierApiTradersAdmin.addTrader(followTraderEntity);
                if (conCodeEnum == ConCodeEnum.SUCCESS) {
                    quoteClient = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(followTraderEntity.getId().toString()).quoteClient;
                    CopierApiTrader copierApiTrader1 = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(followTraderEntity.getId().toString());
                    if (ObjectUtil.isNotEmpty(copierApiTrader1)){
                        copierApiTrader1.setTrader(followTraderEntity);
                        copierApiTrader1.startTrade();
                    }
                } else {
                    log.error(trader.getTrader().getId()+"掉线异常");
                    throw new RuntimeException("登录异常" + trader.getTrader().getId());
                }
            }

            if (ObjectUtil.isEmpty(quoteClient.GetQuote(orderInfo.getSymbol()))){
                //订阅
                quoteClient.Subscribe(orderInfo.getSymbol());
            }
            ip=quoteClient.Host+":"+quoteClient.Port;
            double bidsub =0;
            double asksub =0;
            int loopTimes=1;
            QuoteEventArgs quoteEventArgs = null;
            while (quoteEventArgs == null && quoteClient.Connected()) {
                quoteEventArgs = quoteClient.GetQuote(orderInfo.getSymbol());
                if (++loopTimes > 20) {
                    break;
                } else {
                    Thread.sleep(50);
                }
            }
            bidsub =ObjectUtil.isNotEmpty(quoteEventArgs)?quoteEventArgs.Bid:0;
            asksub =ObjectUtil.isNotEmpty(quoteEventArgs)?quoteEventArgs.Ask:0;
            log.info("下单详情 账号: " + followTraderEntity.getId() + " 品种: " + orderInfo.getSymbol() + " 手数: " + openOrderMapping.getSlaveLots());

            // 执行订单发送
            double startPrice=op.equals(Op.Buy) ? asksub : bidsub;
            // 记录开始时间
            LocalDateTime startTime=LocalDateTime.now();
            long start = System.currentTimeMillis();
            OrderClient oc;
            if (ObjectUtil.isNotEmpty(quoteClient.OrderClient)) {
                oc = quoteClient.OrderClient;
            } else {
                oc = new OrderClient(quoteClient);
            }
            oc.PlacedType=PlacedType.forValue(leaderCopier.getPlacedType());
            Order order = oc.OrderSend(
                    orderInfo.getSymbol(),
                    op,
                    openOrderMapping.getSlaveLots().doubleValue(),
                    startPrice,
                    Integer.MAX_VALUE,
                    BigDecimal.ZERO.doubleValue(),
                    BigDecimal.ZERO.doubleValue(),
                    comment(leaderCopier,orderInfo,followTraderEntity.getServerId()),
                    orderInfo.getTicket(),
                    null
            );
            long end = System.currentTimeMillis();
            log.info("MT4下单时间差 订单:"+order.Ticket+"内部时间差:"+order.sendTimeDifference+"外部时间差:"+(end-start));
            // 记录结束时间
            LocalDateTime endTime = LocalDateTime.now();
            log.info("下单详情 账号: " + followTraderEntity.getId() + " 平台: " + followTraderEntity.getPlatform() + " 节点: " + quoteClient.Host + ":" + quoteClient.Port);
            log.info("请求进入时间结束: " + followTraderEntity.getId());
            openOrderMapping.setCopierOrder(order, orderInfo);
            openOrderMapping.setFlag(CopyTradeFlag.OS);
            openOrderMapping.setExtra("[开仓]即时价格成交");
            followSubscribeOrderService.saveOrUpdate(openOrderMapping);
            cacheCopierOrder(orderInfo, order,openOrderMapping);
            // 创建订单结果事件
            OrderResultEvent event = new OrderResultEvent(order, orderInfo, openOrderMapping, followTraderEntity, flag, startTime, endTime, startPrice, ip);
            ObjectMapper mapper = JacksonConfig.getObjectMapper();
            String jsonEvent = null;
            try {
                jsonEvent = mapper.writeValueAsString(event);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
            // 保存到批量发送队列
            kafkaMessages.add(jsonEvent);
            if (flag==1){
                FollowOrderRepairSocketVO followOrderRepairSocketVO = setRepairWebSocket(leaderCopier.getMasterId().toString(), leaderCopier.getSlaveId().toString(), quoteClient);
                traderOrderRepairWebSocket.pushMessage(leaderCopier.getMasterId().toString(),leaderCopier.getSlaveId().toString(), JsonUtils.toJsonString(followOrderRepairSocketVO));
                redisUtil.set(Constant.TRADER_TEMPORARILY_REPAIR+leaderCopier.getSlaveId(),followOrderRepairSocketVO);
            }
//            //删除漏单redis记录
//            Object o2 = redisUtil.hGetStr(Constant.REPAIR_SEND + openOrderMapping.getMasterAccount() + ":" +openOrderMapping.getMasterId(), openOrderMapping.getSlaveAccount().toString());
//            Map<Integer, OrderRepairInfoVO> repairInfoVOS = new HashMap();
//            if (o2 != null && o2.toString().trim().length() > 0) {
//                repairInfoVOS = JSONObject.parseObject(o2.toString(), Map.class);
//            }
//            repairInfoVOS.remove(orderInfo.getTicket());
//            if(repairInfoVOS==null || repairInfoVOS.size()==0){
//                redisUtil.hDel(Constant.REPAIR_SEND +openOrderMapping.getMasterAccount() + ":" + openOrderMapping.getMasterId(),openOrderMapping.getSlaveAccount().toString());
//            }else{
//                redisUtil.hSetStr(Constant.REPAIR_SEND +openOrderMapping.getMasterAccount() + ":" + openOrderMapping.getMasterId(), openOrderMapping.getSlaveAccount().toString(), JSONObject.toJSONString(repairInfoVOS));
//            }
//            log.info("漏单删除,key:{},key:{},val:{},订单号:{}",Constant.REPAIR_SEND +openOrderMapping.getMasterAccount() + ":" + openOrderMapping.getMasterId(), openOrderMapping.getSlaveAccount().toString(),JSONObject.toJSONString(repairInfoVOS),orderInfo.getTicket() );

        } catch (Exception e) {
            openOrderMapping.setExtra("开仓失败"+e.getMessage());
            followSubscribeOrderService.saveOrUpdate(openOrderMapping, Wrappers.<FollowSubscribeOrderEntity>lambdaUpdate().eq(FollowSubscribeOrderEntity::getMasterId, openOrderMapping.getMasterId()).eq(FollowSubscribeOrderEntity::getMasterTicket, openOrderMapping.getMasterTicket()).eq(FollowSubscribeOrderEntity::getSlaveId, openOrderMapping.getSlaveId()));
            log.error("OrderSend 异常", e);
            FollowOrderDetailEntity followOrderDetailEntity = new FollowOrderDetailEntity();
            followOrderDetailEntity.setTraderId(followTraderEntity.getId());
            followOrderDetailEntity.setAccount(followTraderEntity.getAccount());
            followOrderDetailEntity.setSymbol(orderInfo.getSymbol());
            followOrderDetailEntity.setCreator(SecurityUser.getUserId());
            followOrderDetailEntity.setCreateTime(LocalDateTime.now());
            followOrderDetailEntity.setSendNo("11111");
            followOrderDetailEntity.setType(op.getValue());
            followOrderDetailEntity.setPlacedType(orderInfo.getPlaceType());
            followOrderDetailEntity.setPlatform(followTraderEntity.getPlatform());
            followOrderDetailEntity.setBrokeName(brokeName);
            followOrderDetailEntity.setIpAddr(followTraderEntity.getIpAddr());
            followOrderDetailEntity.setServerName(followTraderEntity.getServerName());
            followOrderDetailEntity.setSize(openOrderMapping.getSlaveLots());
            followOrderDetailEntity.setSourceUser(orderInfo.getAccount());
            followOrderDetailEntity.setServerHost(ip);
            followOrderDetailEntity.setRemark(e.getMessage());
            followOrderDetailService.save(followOrderDetailEntity);
            logFollowOrder(followTraderEntity,orderInfo,openOrderMapping,flag,ip,e.getMessage(),op);
            return false;
        }
        return true;
    }


}
