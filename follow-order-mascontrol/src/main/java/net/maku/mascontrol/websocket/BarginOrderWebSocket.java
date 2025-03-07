package net.maku.mascontrol.websocket;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import net.maku.followcom.entity.*;
import net.maku.followcom.enums.*;
import net.maku.followcom.service.*;
import net.maku.followcom.service.impl.*;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.framework.common.cache.RedisUtil;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.utils.JsonUtils;
import net.maku.mascontrol.vo.FollowBarginOrderVO;
import online.mtapi.mt4.QuoteClient;
import online.mtapi.mt4.QuoteEventArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;

/**
 * 账号信息
 */
@Component
@ServerEndpoint("/socket/bargain/orderSend/{traderId}/{symbol}") //此注解相当于设置访问URL
public class BarginOrderWebSocket {

    private static final Logger log = LoggerFactory.getLogger(BarginOrderWebSocket.class);
    private Session session;
    private String traderId;
    private String symbol;
    private FollowTraderServiceImpl followTraderService= SpringContextUtils.getBean( FollowTraderServiceImpl.class);
    private static Map<String, Set<Session>> sessionPool = new ConcurrentHashMap<>();
    private RedisUtil redisUtil=SpringContextUtils.getBean(RedisUtil.class);;
    private ScheduledFuture<?> scheduledFuture;
    private ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    private static Map<String, Future<?>> scheduledTasks = new ConcurrentHashMap<>(); // 用于存储每个连接的定时任务
    private FollowTraderUserService followTraderUserService= SpringContextUtils.getBean( FollowTraderUserServiceImpl.class);
    private FollowVpsService followVpsService= SpringContextUtils.getBean( FollowVpsServiceImpl.class);
    private FollowPlatformService followPlatformService= SpringContextUtils.getBean( FollowPlatformServiceImpl.class);
    private FollowSysmbolSpecificationService followSysmbolSpecificationService= SpringContextUtils.getBean( FollowSysmbolSpecificationServiceImpl.class);
    private FollowVarietyService followVarietyService= SpringContextUtils.getBean( FollowVarietyServiceImpl.class);
    private FollowOrderInstructService followOrderInstructService= SpringContextUtils.getBean( FollowOrderInstructServiceImpl.class);

    @OnOpen
    public void onOpen(Session session, @PathParam(value = "traderId") String traderId, @PathParam(value = "symbol") String symbol) {
        try {
            this.session = session;
            this.traderId = traderId;
            this.symbol = symbol;

            // 关闭之前的任务（如果存在）
            if (scheduledTasks.containsKey(traderId + symbol)) {
                stopPeriodicTask( traderId+symbol); // 停止之前的任务
            }
            Set<Session> sessionSet;
            if (sessionPool.containsKey(traderId+symbol)) {
                sessionSet = sessionPool.get(traderId+symbol);
            } else {
                sessionSet = new HashSet<>();
            }
            sessionSet.add(session);
            sessionPool.put(traderId+symbol, sessionSet);
            log.info("订阅该品种{}+++{}",symbol,traderId);
            //发送远程调用vps存入报价到redis
            FollowTraderUserEntity followTraderUserEntity = followTraderUserService.getById(traderId);
            //查询各VPS状态正常的账号
            FollowTraderEntity followTrader=new FollowTraderEntity();
            List<FollowTraderEntity> followTraderEntity = followTraderService.list(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getStatus, CloseOrOpenEnum.CLOSE.getValue()).eq(FollowTraderEntity::getAccount, followTraderUserEntity.getAccount()).eq(FollowTraderEntity::getPlatformId, followTraderUserEntity.getPlatformId()).orderByDesc(FollowTraderEntity::getType));
            if (ObjectUtil.isNotEmpty(followTraderEntity)){
                for (FollowTraderEntity fo : followTraderEntity) {
                    //检查vps是否正常
                    FollowVpsEntity followVpsEntity = followVpsService.getById(fo.getServerId());
                    if (followVpsEntity.getIsOpen().equals(CloseOrOpenEnum.CLOSE.getValue()) || followVpsEntity.getConnectionStatus().equals(CloseOrOpenEnum.CLOSE.getValue())) {
                        log.info(followVpsEntity.getName()+"VPS服务异常，请检查");
                    }else {
                        followTrader=fo;
                        break;
                    }
                }
            }
            String serverNode;
            if(ObjectUtil.isNotEmpty(followTrader)){
                FollowPlatformEntity followPlatformServiceOne = followPlatformService.getOne(new LambdaQueryWrapper<FollowPlatformEntity>().eq(FollowPlatformEntity::getServer, followTrader.getPlatform()));
                if (redisUtil.hKeys(Constant.VPS_NODE_SPEED+followTrader.getServerId()).contains(followTrader.getPlatform())&&ObjectUtil.isNotEmpty(redisUtil.hGet(Constant.VPS_NODE_SPEED + followTrader.getServerId(), followTrader.getPlatform()))){
                    serverNode = (String) redisUtil.hGet(Constant.VPS_NODE_SPEED + followTrader.getServerId(), followTrader.getPlatform());
                }  else {
                    serverNode = followPlatformServiceOne.getServerNode();
                }
                if (ObjectUtil.isNotEmpty(serverNode)) {
                    //处理节点格式
                    String[] split = serverNode.split(":");
                    QuoteClient quoteClient = new QuoteClient(Integer.parseInt(followTrader.getAccount()), followTrader.getPassword(),  split[0], Integer.valueOf(split[1]));
                    quoteClient.Connect();
                    log.info(followTrader.getId()+"登录完成");
                    //查品种模版
                    getSymbol(quoteClient,followTrader,followPlatformServiceOne.getBrokerName());
                    startPeriodicTask(quoteClient); // 启动定时任务
                }
            }
        } catch (Exception e) {
            log.info("连接异常"+e);
            throw new RuntimeException();
        }
    }

    private void getSymbol(QuoteClient quoteClient,FollowTraderEntity followTraderEntity,String brokeName) {
        QuoteEventArgs eventArgs = null;
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
            }
        }else {
            // 查看品种匹配 模板
            List<FollowVarietyEntity> followVarietyEntityList =followVarietyService.getListByTemplated(followTraderEntity.getTemplateId());
            List<FollowVarietyEntity> listv =followVarietyEntityList.stream().filter(o->ObjectUtil.isNotEmpty(o.getBrokerName())&&o.getBrokerName().equals(brokeName)&&o.getStdSymbol().equals(symbol)).toList();
            log.info("匹配品种"+listv);
            if (ObjectUtil.isNotEmpty(listv)){
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
                }
            }
        }

    }

    private void startPeriodicTask(QuoteClient client) {
        // 每秒钟发送一次消息
        Future<?> scheduledTask = scheduledExecutorService.scheduleAtFixedRate(() -> sendPeriodicMessage( client,traderId,symbol), 0, 2, TimeUnit.SECONDS);
        scheduledTasks.put(traderId+symbol, scheduledTask); // 将任务保存到任务映射中
    }

    private void stopPeriodicTask(String key) {
        if (scheduledTasks.containsKey(key)) {
            Future<?> task = scheduledTasks.get(key);
            if (task != null && !task.isCancelled()) {
                task.cancel(true); // 取消之前的任务
            }
            scheduledTasks.remove(key); // 移除任务
        }
    }

    private void sendPeriodicMessage(QuoteClient client,String trader,String symbol){
        FollowBarginOrderVO followBarginOrderVO = new FollowBarginOrderVO();
        QuoteEventArgs eventArgs = getEventArgs(client);
        followBarginOrderVO.setBuyPrice(eventArgs.Ask);
        followBarginOrderVO.setSellPrice(eventArgs.Bid);
        //获取最新的正在执行指令
        Optional<FollowOrderInstructEntity> followOrderInstructEntity = followOrderInstructService.list(new LambdaQueryWrapper<FollowOrderInstructEntity>().eq(FollowOrderInstructEntity::getStatus, FollowMasOrderStatusEnum.UNDERWAY.getValue()).eq(FollowOrderInstructEntity::getTraderId, Integer.valueOf(trader)).orderByDesc(FollowOrderInstructEntity::getCreateTime)).stream().findFirst();
        if (followOrderInstructEntity.isPresent()){
            FollowOrderInstructEntity followOrderInstruct = followOrderInstructEntity.get();
            followBarginOrderVO.setStatus(CloseOrOpenEnum.CLOSE.getValue());
            followBarginOrderVO.setScheduleNum(followOrderInstruct.getTradedOrders());
            followBarginOrderVO.setScheduleSuccessNum(followOrderInstruct.getTradedOrders());
            followBarginOrderVO.setScheduleFailNum(followOrderInstruct.getFailOrders());
        }
        pushMessage(trader,symbol, JsonUtils.toJsonString(followBarginOrderVO));
    }


    @OnClose
    public void onClose() {
        try {
            Set<Session> sessionSet = sessionPool.get(traderId+symbol);
            if (ObjectUtil.isEmpty(sessionSet))return;
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

}
