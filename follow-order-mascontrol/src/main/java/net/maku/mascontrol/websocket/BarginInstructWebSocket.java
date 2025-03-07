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
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.enums.FollowMasOrderStatusEnum;
import net.maku.followcom.service.*;
import net.maku.followcom.service.impl.*;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.framework.common.cache.RedisUtil;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.utils.JsonUtils;
import net.maku.mascontrol.vo.FollowBaiginInstructVO;
import net.maku.mascontrol.vo.FollowBarginOrderVO;
import online.mtapi.mt4.QuoteClient;
import online.mtapi.mt4.QuoteEventArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;

/**
 * 下单指令
 */
@Component
@ServerEndpoint("/socket/bargain/instruct") //此注解相当于设置访问URL
public class BarginInstructWebSocket {

    private static final Logger log = LoggerFactory.getLogger(BarginInstructWebSocket.class);
    private Session session;
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
    public void onOpen(Session session) {
        try {
            this.session = session;
            if (scheduledExecutorService.isShutdown()){
                startPeriodicTask();
            }
        } catch (Exception e) {
            log.info("连接异常"+e);
            throw new RuntimeException();
        }
    }


    private void startPeriodicTask() {
        // 每秒钟发送一次消息
        scheduledExecutorService.scheduleAtFixedRate(() -> sendPeriodicMessage(), 0, 2, TimeUnit.SECONDS);
    }

    private void stopPeriodicTask() {
        scheduledExecutorService.shutdown();
    }

    private void sendPeriodicMessage(){
        FollowBaiginInstructVO followBaiginInstructVO = new FollowBaiginInstructVO();
        //获取最新的正在执行指令
//        Optional<FollowOrderInstructEntity> followOrderInstructEntity = followOrderInstructService.list(new LambdaQueryWrapper<FollowOrderInstructEntity>().eq(FollowOrderInstructEntity::getStatus, FollowMasOrderStatusEnum.UNDERWAY.getValue()).orderByDesc(FollowOrderInstructEntity::getCreateTime)).stream().findFirst();
//        if (followOrderInstructEntity.isPresent()){
//            FollowOrderInstructEntity followOrderInstruct = followOrderInstructEntity.get();
//            followBaiginInstructVO.setStatus(CloseOrOpenEnum.CLOSE.getValue());
//            followBaiginInstructVO.setScheduleNum(followOrderInstruct.getTradedOrders());
//            followBaiginInstructVO.setScheduleSuccessNum(followOrderInstruct.getTradedOrders());
//            followBaiginInstructVO.setScheduleFailNum(followOrderInstruct.getFailOrders());
//        }
//        pushMessage(JsonUtils.toJsonString(followBarginOrderVO));
    }


    @OnClose
    public void onClose() {
        try {
            if (scheduledExecutorService.isTerminated()){
                scheduledExecutorService.isShutdown();
            }
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
