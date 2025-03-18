package net.maku.mascontrol.websocket;

import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
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
import net.maku.followcom.util.AesUtils;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.followcom.vo.OrderActiveInfoVO;
import net.maku.framework.common.cache.RedisUtil;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.utils.JsonUtils;
import net.maku.mascontrol.vo.FollowBarginOrderVO;
import online.mtapi.mt4.QuoteClient;
import online.mtapi.mt4.QuoteEventArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

import static dm.jdbc.util.DriverUtil.log;

/**
 * 账号信息
 */
@Component
@ServerEndpoint("/socket/bargain/orderSend") //此注解相当于设置访问URL
public class BarginOrderWebSocket {

    private static final Logger log = LoggerFactory.getLogger(BarginOrderWebSocket.class);
    private FollowTraderServiceImpl followTraderService= SpringContextUtils.getBean( FollowTraderServiceImpl.class);
    private static Map<String, Set<Session>> sessionPool = new ConcurrentHashMap<>();
    private RedisUtil redisUtil=SpringContextUtils.getBean(RedisUtil.class);;
    private ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(0,Thread.ofVirtual().factory());;
    private static Map<String, Future<?>> scheduledTasks = new ConcurrentHashMap<>(); // 用于存储每个连接的定时任务
    private FollowTraderUserService followTraderUserService= SpringContextUtils.getBean( FollowTraderUserServiceImpl.class);
    private FollowVpsService followVpsService= SpringContextUtils.getBean( FollowVpsServiceImpl.class);
    private FollowPlatformService followPlatformService= SpringContextUtils.getBean( FollowPlatformServiceImpl.class);
    private FollowSysmbolSpecificationService followSysmbolSpecificationService= SpringContextUtils.getBean( FollowSysmbolSpecificationServiceImpl.class);
    private FollowVarietyService followVarietyService= SpringContextUtils.getBean( FollowVarietyServiceImpl.class);
    private FollowOrderInstructService followOrderInstructService= SpringContextUtils.getBean( FollowOrderInstructServiceImpl.class);
    private ConcurrentHashMap<String, QuoteClient> quoteClientConcurrentHashMap=new ConcurrentHashMap<>();
    @OnOpen
    public void onOpen(Session session) {
    }

    private String getSymbol(String traderId,String symbol,QuoteClient quoteClient,FollowTraderEntity followTraderEntity,String brokeName) {
        QuoteEventArgs eventArgs = null;
        //获取symbol信息
        String finalSymbol = symbol;
        List<FollowSysmbolSpecificationEntity> specificationServiceByTraderId = followSysmbolSpecificationService.getByTraderId(Long.parseLong(traderId)).stream().filter(o->o.getSymbol().contains(finalSymbol)).toList();
        if (ObjectUtil.isNotEmpty(specificationServiceByTraderId)){
            for (FollowSysmbolSpecificationEntity o:specificationServiceByTraderId) {
                symbol=o.getSymbol();
                eventArgs = getEventArgs(symbol,quoteClient);
                log.info("品种规格symbol"+symbol);
                if (ObjectUtil.isNotEmpty(eventArgs)){
                    break;
                }
            }
            if (ObjectUtil.isEmpty(eventArgs)){
                return "";
            }
        }else {
            // 查看品种匹配 模板
            List<FollowVarietyEntity> followVarietyEntityList =followVarietyService.getListByTemplated(followTraderEntity.getTemplateId());
            String finalSymbol1 = symbol;
            List<FollowVarietyEntity> listv =followVarietyEntityList.stream().filter(o->ObjectUtil.isNotEmpty(o.getBrokerName())&&o.getBrokerName().equals(brokeName)&&o.getStdSymbol().equals(finalSymbol1)).toList();
            log.info("匹配品种"+listv);
            if (ObjectUtil.isNotEmpty(listv)){
                for (FollowVarietyEntity o:listv){
                    if (ObjectUtil.isNotEmpty(o.getBrokerSymbol())){
                        //查看品种规格
                        log.info("匹配symbol"+o.getBrokerSymbol());
                        symbol=o.getBrokerSymbol();
                        eventArgs = getEventArgs(symbol,quoteClient);
                        if (ObjectUtil.isNotEmpty(eventArgs)){
                            break;
                        }
                    }
                }
                if (ObjectUtil.isEmpty(eventArgs)){
                    return "";
                }
            }
        }
        return symbol;

    }

    private void startPeriodicTask(String traderId,String symbol,QuoteClient client,Session session) {
        // 每秒钟发送一次消息
        Future<?> scheduledTask = scheduledExecutorService.scheduleAtFixedRate(() -> sendPeriodicMessage( client,traderId,symbol,session), 0, 1, TimeUnit.SECONDS);
        scheduledTasks.put(session.getId(), scheduledTask); // 将任务保存到任务映射中
    }

    private void sendPeriodicMessage(QuoteClient client,String trader,String symbol,Session session){
        FollowBarginOrderVO followBarginOrderVO = new FollowBarginOrderVO();
        QuoteEventArgs eventArgs = getEventArgs(symbol,client);
        followBarginOrderVO.setBuyPrice(eventArgs.Ask);
        followBarginOrderVO.setSellPrice(eventArgs.Bid);
        //获取最新的正在执行指令
        Optional<FollowOrderInstructEntity> followOrderInstructEntity = followOrderInstructService.list(new LambdaQueryWrapper<FollowOrderInstructEntity>().eq(FollowOrderInstructEntity::getStatus, FollowMasOrderStatusEnum.UNDERWAY.getValue()).eq(FollowOrderInstructEntity::getTraderId, Integer.valueOf(trader)).orderByDesc(FollowOrderInstructEntity::getCreateTime)).stream().findFirst();
        if (followOrderInstructEntity.isPresent()){
            FollowOrderInstructEntity followOrderInstruct = followOrderInstructEntity.get();
            followBarginOrderVO.setStatus(CloseOrOpenEnum.CLOSE.getValue());
            followBarginOrderVO.setScheduleNum(followOrderInstruct.getTrueTotalOrders());
            followBarginOrderVO.setScheduleSuccessNum(followOrderInstruct.getTradedOrders());
            followBarginOrderVO.setScheduleFailNum(followOrderInstruct.getFailOrders());
        }else {
            followBarginOrderVO.setStatus(CloseOrOpenEnum.OPEN.getValue());
        }
        pushMessage(session,JsonUtils.toJsonString(followBarginOrderVO));
    }


    @OnClose
    public void onClose(Session session) {
        try {
            String id = session.getId();
            Future<?> future = scheduledTasks.get(id);
            if (future != null && !future.isCancelled()) {
                future.cancel(true);
            }
            if (session != null && session.getBasicRemote() != null) {
                session.close();
            }

        } catch (IOException e) {
            log.error("关闭链接异常{}", e);
            throw new RuntimeException(e);
        }

    }

    /**
     * 服务器端推送消息
     */
    public void pushMessage(Session session,String message) {
        try {
            if (session != null && session.getBasicRemote() != null) {
                synchronized (session) {
                    session.getBasicRemote().sendText(message);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @OnMessage
    public void onMessage(String message,Session session) {

        JSONObject jsonObj = JSONObject.parseObject(message);
        String symbol = jsonObj.getString("symbol");
        String traderIds = jsonObj.getString("traderId");
        //多账号获取数据
        String[] traders = traderIds.split(",");
        String id = session.getId();
        Future<?> st = scheduledTasks.get(id);
        if (st != null) {
            st.cancel(true);
            scheduledTasks.clear();
        }
        for(String traderId:traders){
            //发送远程调用vps存入报价到redis
            FollowTraderUserEntity followTraderUserEntity = followTraderUserService.getById(traderId);
            //查询各VPS状态正常的账号
            FollowTraderEntity followTrader=null;
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
                    try {
                        QuoteClient quoteClient =quoteClientConcurrentHashMap.get(followTrader.getId().toString());
                        if (ObjectUtil.isEmpty(quoteClient)){
                            quoteClient =loginMt4(session,followTrader.getId().toString(),followTrader.getAccount(),followTrader.getPassword(),split);
                        }else {
                            if (!quoteClient.Connected()){
                                quoteClient =loginMt4(session,followTrader.getId().toString(),followTrader.getAccount(),followTrader.getPassword(),split);
                            }
                        }
                        //查品种模版
                        String symbol1 = getSymbol(followTrader.getId().toString(), symbol, quoteClient, followTrader, followPlatformServiceOne.getBrokerName());
                        if (ObjectUtil.isEmpty(symbol1)){
                            continue;
                        }
                        if (!scheduledTasks.containsKey(id)) {
                            startPeriodicTask(followTrader.getId().toString(), symbol1, quoteClient,session); // 启动定时任务
                            break;
                        }
                    }catch (Exception e){
                        log.info("连接异常");
                    }
                }
            }
        }
    }

    private QuoteClient loginMt4(Session session,String traderId,String account, String password, String[] s) {
        try {
            QuoteClient  quoteClient = new QuoteClient(Integer.parseInt(account), AesUtils.decryptStr(password),  s[0], Integer.valueOf(s[1]));
            quoteClient.Connect();
            quoteClientConcurrentHashMap.put(traderId,quoteClient);
            log.info(account+"登录完成");
            return quoteClient;
        }catch (Exception e){
            if (e.getMessage().contains("Invalid account")) {
                log.info("账户密码登录异常");
            }
            log.info("BarginOrderWebSocket登录异常"+e.getMessage());
            FollowBarginOrderVO followBarginOrderVO = new FollowBarginOrderVO();
            followBarginOrderVO.setStatus(2);
            pushMessage(session,JsonUtils.toJsonString(followBarginOrderVO));
            return null;
        }
    }

    public Boolean isConnection(String traderId,String symbol) {
        return sessionPool.containsKey(traderId+symbol);
    }


    private QuoteEventArgs getEventArgs(String symbol,QuoteClient quoteClient){
        QuoteEventArgs eventArgs = null;
        try {
            log.info("quoteClient.GetQuote(symbol)"+quoteClient.GetQuote(symbol)+quoteClient.Connected());
            if (ObjectUtil.isEmpty(quoteClient.GetQuote(symbol))){
                //订阅
                quoteClient.Subscribe(symbol);
            }
            while (eventArgs==null && quoteClient.Connected()) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                eventArgs=quoteClient.GetQuote(symbol);
            }
            return eventArgs;
        }catch (Exception e) {
            log.info("获取报价失败,品种不正确,请先配置品种");
            return eventArgs;
        }
    }

}
