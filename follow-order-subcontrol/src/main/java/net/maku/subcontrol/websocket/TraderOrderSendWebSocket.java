package net.maku.subcontrol.websocket;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import net.maku.followcom.entity.FollowOrderSendEntity;
import net.maku.followcom.entity.FollowSysmbolSpecificationEntity;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.enums.ConCodeEnum;
import net.maku.followcom.enums.TraderTypeEnum;
import net.maku.followcom.service.FollowOrderSendService;
import net.maku.followcom.service.FollowPlatformService;
import net.maku.followcom.service.impl.FollowOrderSendServiceImpl;
import net.maku.followcom.service.impl.FollowSysmbolSpecificationServiceImpl;
import net.maku.followcom.service.impl.FollowTraderServiceImpl;
import net.maku.followcom.util.FollowConstant;
import net.maku.framework.common.cache.RedisCache;
import net.maku.framework.common.cache.RedisUtil;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.exception.ServerException;
import net.maku.framework.common.utils.JsonUtils;
import net.maku.followcom.entity.FollowPlatformEntity;
import net.maku.followcom.entity.FollowVarietyEntity;
import net.maku.followcom.service.impl.FollowPlatformServiceImpl;
import net.maku.followcom.service.impl.FollowVarietyServiceImpl;
import net.maku.subcontrol.trader.*;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.subcontrol.vo.FollowOrderSendSocketVO;
import online.mtapi.mt4.Exception.ConnectException;
import online.mtapi.mt4.Exception.InvalidSymbolException;
import online.mtapi.mt4.Exception.TimeoutException;
import online.mtapi.mt4.QuoteClient;
import online.mtapi.mt4.QuoteEventArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 品种报价及订单状态推送
 */
@Component
@ServerEndpoint("/socket/trader/orderSend/{traderId}/{symbol}") //此注解相当于设置访问URL
public class TraderOrderSendWebSocket {

    private static final Logger log = LoggerFactory.getLogger(TraderOrderSendWebSocket.class);
    private Session session;

    private String traderId;

    private String symbol;

    private FollowVarietyServiceImpl followVarietyService= SpringContextUtils.getBean( FollowVarietyServiceImpl.class);
    private FollowTraderServiceImpl followTraderService= SpringContextUtils.getBean( FollowTraderServiceImpl.class);
    private RedisCache redisCache= SpringContextUtils.getBean( RedisCache.class);
    private FollowSysmbolSpecificationServiceImpl followSysmbolSpecificationService= SpringContextUtils.getBean( FollowSysmbolSpecificationServiceImpl.class);

    private static Map<String, Set<Session>> sessionPool = new ConcurrentHashMap<>();

    private  RedisUtil redisUtil=SpringContextUtils.getBean(RedisUtil.class);;
    private FollowOrderSendService followOrderSendService=SpringContextUtils.getBean(FollowOrderSendServiceImpl.class);
    private LeaderApiTradersAdmin leaderApiTradersAdmin= SpringContextUtils.getBean(LeaderApiTradersAdmin.class);
    private CopierApiTradersAdmin copierApiTradersAdmin= SpringContextUtils.getBean(CopierApiTradersAdmin.class);

    private FollowPlatformService followPlatformService=SpringContextUtils.getBean(FollowPlatformServiceImpl.class);
    private ScheduledFuture<?> scheduledFuture;
    private ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();

    @OnOpen
    public void onOpen(Session session, @PathParam(value = "traderId") String traderId, @PathParam(value = "symbol") String symbol) {
        try {
            this.session = session;
            this.traderId = traderId;
            this.symbol = symbol;
            Set<Session> sessionSet;
            if (sessionPool.containsKey(traderId+symbol)) {
                sessionSet = sessionPool.get(traderId+symbol);
            } else {
                sessionSet = new HashSet<>();
            }
            sessionSet.add(session);
            sessionPool.put(traderId+symbol, sessionSet);
            log.info("订阅该品种{}+++{}",symbol,traderId);
            FollowTraderEntity followTraderEntity=followTraderService.getById(Long.valueOf(traderId));
            QuoteClient quoteClient = null;
            AbstractApiTrader abstractApiTrader;
            if (followTraderEntity.getType().equals(TraderTypeEnum.MASTER_REAL.getType())) {
                abstractApiTrader =leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(traderId);
                if (ObjectUtil.isEmpty(abstractApiTrader) || ObjectUtil.isEmpty(abstractApiTrader.quoteClient)
                        || !abstractApiTrader.quoteClient.Connected()) {
                    log.info("sendWebSocketaddTrader"+traderId);
                    leaderApiTradersAdmin.removeTrader(traderId);
                    ConCodeEnum conCodeEnum = leaderApiTradersAdmin.addTrader(followTraderEntity);
                    if (conCodeEnum == ConCodeEnum.SUCCESS ) {
                        quoteClient=leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(followTraderEntity.getId().toString()).quoteClient;
                        LeaderApiTrader leaderApiTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(traderId);
                        leaderApiTrader.startTrade();
                    }else if (conCodeEnum == ConCodeEnum.AGAIN){
                        long maxWaitTimeMillis = 10000; // 最多等待10秒
                        long startTime = System.currentTimeMillis();
                        LeaderApiTrader leaderApiTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(traderId);
                        // 开始等待直到获取到copierApiTrader1
                        while (leaderApiTrader == null && (System.currentTimeMillis() - startTime) < maxWaitTimeMillis) {
                            try {
                                // 每次自旋等待500ms后再检查
                                Thread.sleep(500);
                            } catch (InterruptedException e) {
                                // 处理中断
                                Thread.currentThread().interrupt();
                                break;
                            }
                            leaderApiTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(traderId);
                        }
                        //重复提交
                        if (ObjectUtil.isNotEmpty(leaderApiTrader)){
                            log.info(traderId+"重复提交并等待完成");
                            quoteClient = leaderApiTrader.quoteClient;
                        }else {
                            log.info(traderId+"重复提交并等待失败");
                        }
                    } else {
                        quoteClient = null;
                    }
                }else {
                    quoteClient=abstractApiTrader.quoteClient;
                }
            }else {
                abstractApiTrader =copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(traderId);
                if (ObjectUtil.isEmpty(abstractApiTrader) || ObjectUtil.isEmpty(abstractApiTrader.quoteClient)
                        || !abstractApiTrader.quoteClient.Connected()) {
                    copierApiTradersAdmin.removeTrader(traderId);
                    ConCodeEnum conCodeEnum = copierApiTradersAdmin.addTrader(followTraderEntity);
                    if (conCodeEnum == ConCodeEnum.SUCCESS ) {
                        quoteClient=copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(followTraderEntity.getId().toString()).quoteClient;
                        CopierApiTrader copierApiTrader = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(traderId);
                        copierApiTrader.startTrade();
                    }else if (conCodeEnum == ConCodeEnum.AGAIN){
                        long maxWaitTimeMillis = 10000; // 最多等待10秒
                        long startTime = System.currentTimeMillis();
                        CopierApiTrader copierApiTrader = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(traderId);
                        // 开始等待直到获取到copierApiTrader1
                        while (copierApiTrader == null && (System.currentTimeMillis() - startTime) < maxWaitTimeMillis) {
                            try {
                                // 每次自旋等待500ms后再检查
                                Thread.sleep(500);
                            } catch (InterruptedException e) {
                                // 处理中断
                                Thread.currentThread().interrupt();
                                break;
                            }
                            copierApiTrader = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(traderId);
                        }
                        //重复提交
                        if (ObjectUtil.isNotEmpty(copierApiTrader)){
                            log.info(traderId+"重复提交并等待完成");
                            quoteClient = copierApiTrader.quoteClient;
                        }else {
                            log.info(traderId+"重复提交并等待失败");
                        }
                    } else {
                        quoteClient = null;
                    }
                }else {
                    quoteClient=abstractApiTrader.quoteClient;
                }
            }

            if (ObjectUtil.isEmpty(quoteClient)){
                throw new ServerException(traderId+"登录异常");
            }
            QuoteEventArgs eventArgs = null;

            //查询平台信息
            FollowPlatformEntity followPlatform = followPlatformService.getPlatFormById(followTraderEntity.getPlatformId().toString());
            //获取symbol信息
            List<FollowSysmbolSpecificationEntity> specificationServiceByTraderId = followSysmbolSpecificationService.getByTraderId(Long.parseLong(traderId)).stream().filter(o->o.getSymbol().contains(symbol)).toList();
            if (ObjectUtil.isNotEmpty(specificationServiceByTraderId)){
                for (FollowSysmbolSpecificationEntity o:specificationServiceByTraderId) {
                    this.symbol=o.getSymbol();
                    eventArgs = getEventArgs(quoteClient);
                    log.info("品种规格symbol"+this.symbol);
                    if (ObjectUtil.isNotEmpty(eventArgs)){
                        break;
                    }
                }
                if (ObjectUtil.isEmpty(eventArgs)){
                    this.symbol=symbol;
                    eventArgs = getEventArgs(quoteClient);
                }
            }else {
                // 查看品种匹配 模板
                List<FollowVarietyEntity> followVarietyEntityList =followVarietyService.getListByTemplated(followTraderEntity.getTemplateId());
                List<FollowVarietyEntity> listv =followVarietyEntityList.stream().filter(o->ObjectUtil.isNotEmpty(o.getBrokerName())&&o.getBrokerName().equals(followPlatform.getBrokerName())&&o.getStdSymbol().equals(symbol)).toList();
                log.info("匹配品种"+listv);
                if (ObjectUtil.isEmpty(listv)){
                    eventArgs = getEventArgs(quoteClient);
                }else {
                    for (FollowVarietyEntity o:listv){
                        if (ObjectUtil.isNotEmpty(o.getBrokerSymbol())){
                            //查看品种规格
                            log.info("匹配symbol"+o.getBrokerSymbol());
                            this.symbol=o.getBrokerSymbol();
                            eventArgs = getEventArgs(quoteClient);
                            if (ObjectUtil.isNotEmpty(eventArgs)){
                                break;
                            }
                        }
                    }
                    if (ObjectUtil.isEmpty(eventArgs)){
                        this.symbol=symbol;
                        eventArgs = getEventArgs(quoteClient);
                    }
                }
            }

            //开启定时任务
            QuoteEventArgs finalEventArgs = eventArgs;
            this.scheduledFuture = scheduledExecutorService.scheduleAtFixedRate(() -> {
                try {
                    sendPeriodicMessage(followTraderEntity, finalEventArgs,symbol);
                } catch (Exception e) {
                    log.info("WebSocket建立连接异常" + e);
                    throw new RuntimeException();
                }
            }, 0, 2, TimeUnit.SECONDS);

        } catch (Exception e) {
            log.info("连接异常"+e);
            throw new RuntimeException();
        }
    }

    private QuoteEventArgs getEventArgs(QuoteClient quoteClient){
        QuoteEventArgs eventArgs = null;
        try {
            log.info("quoteClient.GetQuote(this.symbol)"+quoteClient.GetQuote(this.symbol)+quoteClient.Connected());
            if (ObjectUtil.isEmpty(quoteClient.GetQuote(this.symbol))){
                //订阅
                quoteClient.Subscribe(this.symbol);
            }
            while (eventArgs==null && quoteClient.Connected()) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                eventArgs=quoteClient.GetQuote(this.symbol);
            }
            return eventArgs;
        }catch (Exception e) {
            log.info("获取报价失败,品种不正确,请先配置品种");
            return eventArgs;
        }
    }

    private void sendPeriodicMessage(FollowTraderEntity trader,QuoteEventArgs eventArgs,String symbol){
        if (eventArgs != null) {
            //立即查询
            //查看当前账号订单完成进度
            List<FollowOrderSendEntity> list= followOrderSendService.list(new LambdaQueryWrapper<FollowOrderSendEntity>().eq(FollowOrderSendEntity::getTraderId,trader.getId()));
            FollowOrderSendSocketVO followOrderSendSocketVO=new FollowOrderSendSocketVO();
            followOrderSendSocketVO.setSellPrice(eventArgs.Bid);
            followOrderSendSocketVO.setBuyPrice(eventArgs.Ask);
            followOrderSendSocketVO.setStatus(CloseOrOpenEnum.OPEN.getValue());
            if (ObjectUtil.isNotEmpty(list)){
                List<FollowOrderSendEntity> collect = list.stream().filter(o -> o.getStatus().equals(CloseOrOpenEnum.CLOSE.getValue()) && o.getSymbol().equals(symbol)).collect(Collectors.toList());
                if (ObjectUtil.isNotEmpty(collect)){
                    //是否存在正在执行 进度
                    FollowOrderSendEntity followOrderSendEntity = collect.get(0);
                    followOrderSendSocketVO.setStatus(followOrderSendEntity.getStatus());
                    followOrderSendSocketVO.setScheduleNum(followOrderSendEntity.getTotalNum());
                    followOrderSendSocketVO.setScheduleSuccessNum(followOrderSendEntity.getSuccessNum());
                }
            }
            pushMessage(trader.getId().toString(),symbol, JsonUtils.toJsonString(followOrderSendSocketVO));
        }
    }


    @OnClose
    public void onClose() {
        try {
            sessionPool.get(traderId+symbol).remove(session);
            log.info("取消订阅该品种{}++++{}",symbol,traderId);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 服务器端推送消息
     */
    public void pushMessage(String traderId,String symbol, String message) {
        try {
            Set<Session> sessionSet = sessionPool.get(traderId+symbol);
            if (ObjectUtil.isEmpty(sessionSet))return;
            for (Session session : sessionSet) {
                if (session.isOpen()) {
                    synchronized (session) {
                        session.getBasicRemote().sendText(message);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @OnMessage
    public void onMessage(String message) {
    }

    public Boolean isConnection(String traderId,String symbol) {
        return sessionPool.containsKey(traderId+symbol);
    }

}
