package net.maku.subcontrol.websocket;

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
import net.maku.followcom.enums.ConCodeEnum;
import net.maku.followcom.enums.TraderTypeEnum;
import net.maku.followcom.query.FollowTraderQuery;
import net.maku.followcom.service.FollowOrderSendService;
import net.maku.followcom.service.impl.FollowOrderSendServiceImpl;
import net.maku.followcom.service.impl.FollowSysmbolSpecificationServiceImpl;
import net.maku.followcom.service.impl.FollowTraderServiceImpl;
import net.maku.followcom.service.impl.FollowVarietyServiceImpl;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.followcom.vo.FollowRedisTraderVO;
import net.maku.followcom.vo.FollowTraderVO;
import net.maku.framework.common.cache.RedisCache;
import net.maku.framework.common.cache.RedisUtil;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.exception.ServerException;
import net.maku.framework.common.utils.JsonUtils;
import net.maku.framework.common.utils.PageResult;
import net.maku.subcontrol.even.OnQuoteHandler;
import net.maku.subcontrol.trader.LeaderApiTrader;
import net.maku.subcontrol.trader.LeaderApiTradersAdmin;
import net.maku.subcontrol.vo.FollowOrderSendSocketVO;
import online.mtapi.mt4.Exception.ConnectException;
import online.mtapi.mt4.Exception.InvalidSymbolException;
import online.mtapi.mt4.QuoteClient;
import online.mtapi.mt4.QuoteEventArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 账号信息
 */
@Component
@ServerEndpoint("/socket/trader/master/{page}/{limit}/{number}") //此注解相当于设置访问URL
public class TraderAccountWebSocket {

    private static final Logger log = LoggerFactory.getLogger(TraderAccountWebSocket.class);
    private String page;
    private Session session;

    private String limit;
    private String number;
    private static Map<String, Set<Session>> sessionPool = new ConcurrentHashMap<>();

    private FollowTraderServiceImpl followTraderService= SpringContextUtils.getBean( FollowTraderServiceImpl.class);
    private RedisCache redisCache= SpringContextUtils.getBean( RedisCache.class);
    private List<FollowTraderVO> listFollow=new ArrayList<>();
    private ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    private Future<?> scheduledTask;

    @OnOpen
    public void onOpen(Session session, @PathParam(value = "page") String page, @PathParam(value = "limit") String limit, @PathParam(value = "number") String number) {
        try {
            this.session = session;
            this.page = page;
            this.limit = limit;
            this.number = number;

            Set<Session> sessionSet = sessionPool.getOrDefault(page + limit+number, ConcurrentHashMap.newKeySet());
            sessionSet.add(session);
            sessionPool.put(page + limit+number, sessionSet);
            FollowTraderQuery followTraderQuer=new FollowTraderQuery();
            followTraderQuer.setPage(Integer.valueOf(page));
            followTraderQuer.setType(TraderTypeEnum.MASTER_REAL.getType());
            followTraderQuer.setLimit(Integer.valueOf(limit));
            followTraderQuer.setServerIp(FollowConstant.LOCAL_HOST);
            PageResult<FollowTraderVO> pageResult = followTraderService.page(followTraderQuer);
            if (ObjectUtil.isNotEmpty(pageResult)&&ObjectUtil.isNotEmpty(pageResult.getList())){
                listFollow=pageResult.getList();
                startPeriodicTask();
            }
        } catch (Exception e) {
            log.info("连接异常"+e);
            e.printStackTrace();
            throw new RuntimeException();
        }
    }

    private void startPeriodicTask() {
        // 每秒钟发送一次消息
        scheduledTask = scheduledExecutorService.scheduleAtFixedRate(() -> sendPeriodicMessage(page, limit,number), 0, 1, TimeUnit.SECONDS);
    }

    private void stopPeriodicTask() {
        if (scheduledTask != null && !scheduledTask.isCancelled()) {
            scheduledTask.cancel(true);
        }
    }


    private void sendPeriodicMessage(String page ,String limit,String number) {
        //查询用户数据
        List<FollowRedisTraderVO> followRedisTraderVOS=new ArrayList<>();
        listFollow.forEach(o->followRedisTraderVOS.add((FollowRedisTraderVO) redisCache.get(Constant.TRADER_USER + o.getId())));
        pushMessage(page,limit,number,JsonUtils.toJsonString(followRedisTraderVOS));
    }


    @OnClose
    public void onClose() {
        try {
            sessionPool.get(page + limit+number).remove(session);
            stopPeriodicTask(); // 关闭时停止定时任务
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 服务器端推送消息
     */
    public void pushMessage(String page, String limit,String number, String message) {
        try {
            Set<Session> sessionSet = sessionPool.get(page + limit+number);
            if (ObjectUtil.isEmpty(sessionSet)) {
                return;
            }
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

    public Boolean isConnection(String page, String limit,String number) {
        return sessionPool.containsKey(page + limit+number);
    }

}
