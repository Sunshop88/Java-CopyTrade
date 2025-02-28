package net.maku.subcontrol.websocket;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import net.maku.followcom.entity.*;
import net.maku.followcom.enums.ConCodeEnum;
import net.maku.followcom.pojo.EaOrderInfo;
import net.maku.followcom.service.FollowOrderDetailService;
import net.maku.followcom.service.FollowTraderService;
import net.maku.followcom.service.FollowTraderSubscribeService;
import net.maku.followcom.service.impl.*;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.followcom.vo.OrderActiveInfoVO;
import net.maku.followcom.vo.OrderRepairInfoVO;
import net.maku.framework.common.cache.RedisCache;
import net.maku.framework.common.cache.RedisUtil;
import net.maku.framework.common.cache.RedissonLockUtil;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.exception.ServerException;
import net.maku.framework.common.utils.JsonUtils;
import net.maku.followcom.enums.TraderRepairOrderEnum;
import net.maku.subcontrol.trader.*;
import net.maku.subcontrol.vo.FollowOrderActiveSocketVO;
import online.mtapi.mt4.Op;
import online.mtapi.mt4.Order;
import online.mtapi.mt4.QuoteClient;
import org.checkerframework.checker.units.qual.A;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static online.mtapi.mt4.Op.Buy;
import static online.mtapi.mt4.Op.Sell;

@Component
@ServerEndpoint("/socket/trader/orderActive/{traderId}/{slaveId}") //此注解相当于设置访问URL
public class TraderOrderActiveWebSocket {

    private static final Logger log = LoggerFactory.getLogger(TraderOrderActiveWebSocket.class);
    private final RedissonLockUtil redissonLockUtil=SpringContextUtils.getBean(RedissonLockUtil.class);

    private Session session;

    private String traderId;

    private String slaveId;

    private RedisCache redisCache = SpringContextUtils.getBean(RedisCache.class);

    private static Map<String, Set<Session>> sessionPool = new ConcurrentHashMap<>();
    private RedisUtil redisUtil = SpringContextUtils.getBean(RedisUtil.class);
    private LeaderApiTradersAdmin leaderApiTradersAdmin = SpringContextUtils.getBean(LeaderApiTradersAdmin.class);
    private CopierApiTradersAdmin copierApiTradersAdmin = SpringContextUtils.getBean(CopierApiTradersAdmin.class);
    private FollowTraderSubscribeService followTraderSubscribeService = SpringContextUtils.getBean(FollowTraderSubscribeServiceImpl.class);
    private FollowTraderService followTraderService = SpringContextUtils.getBean(FollowTraderServiceImpl.class);
    private ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> scheduledFuture;
    private FollowOrderDetailService followOrderDetailService = SpringContextUtils.getBean(FollowOrderDetailServiceImpl.class);




    @OnOpen
    public void onOpen(Session session, @PathParam(value = "traderId") String traderId, @PathParam(value = "slaveId") String slaveId) {
        try {
            this.session = session;
            this.traderId = traderId;
            this.slaveId = slaveId;
            Set<Session> sessionSet = sessionPool.getOrDefault(traderId + slaveId, ConcurrentHashMap.newKeySet());
            sessionSet.add(session);
            sessionPool.put(traderId + slaveId, sessionSet);
            //开启定时任务
            this.scheduledFuture = scheduledExecutorService.scheduleAtFixedRate(() -> {
                try {
                    sendPeriodicMessage(traderId, slaveId);
                } catch (Exception e) {
                    log.info("WebSocket建立连接异常" + e);
                }
            }, 0, 2, TimeUnit.SECONDS);

        } catch (Exception e) {
            log.info("连接异常" + e);
            throw new RuntimeException(e);
        }
    }

    public void sendPeriodicMessage(String traderId, String slaveId) {

        try {
            Set<Session> sessionSet = sessionPool.get(traderId + slaveId);
            if (ObjectUtil.isEmpty(sessionSet)) {
                return;
            }
            String accountId = slaveId;
            AbstractApiTrader abstractApiTrader;
            FollowTraderEntity followTraderEntity;
            QuoteClient quoteClient = null;
            if (slaveId.equals("0")) {
                //喊单
                accountId = traderId;
                followTraderEntity = followTraderService.getFollowById(Long.valueOf(accountId));
                abstractApiTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(accountId);
                if (ObjectUtil.isNotEmpty(abstractApiTrader)){
                    quoteClient = abstractApiTrader.quoteClient;
                }
            } else {
                followTraderEntity = followTraderService.getById(Long.valueOf(accountId));
                abstractApiTrader = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(accountId);
                if (ObjectUtil.isNotEmpty(abstractApiTrader)){
                    quoteClient = abstractApiTrader.quoteClient;
                }
            }
            if (ObjectUtil.isEmpty(quoteClient)) {
                log.info(accountId + "登录异常");
                return;
            }
            List<Order> openedOrders = Arrays.stream(abstractApiTrader.quoteClient.GetOpenedOrders()).filter(order1 -> order1.Type == Buy || order1.Type == Sell).collect(Collectors.toList());
            FollowOrderActiveSocketVO followOrderActiveSocketVO = new FollowOrderActiveSocketVO();
            followOrderActiveSocketVO.setOrderActiveInfoList(convertOrderActive(openedOrders,followTraderEntity.getAccount()));
            pushMessage(traderId, slaveId, JsonUtils.toJsonString(followOrderActiveSocketVO));
        } catch (Exception e) {
            log.error("定时推送消息异常", e);
        }finally {
            if (!Thread.currentThread().isInterrupted()) { // 检查线程是否被中断
                redissonLockUtil.unlock("LOCK:" + traderId + ":" + slaveId);
            } else {
                log.warn("线程已中断，跳过释放锁");
            }
        }
    }

    private void stopPeriodicTask() {
        if (scheduledFuture != null && !scheduledFuture.isCancelled()) {
            scheduledFuture.cancel(false); // 避免中断线程
        }
    }

    @OnClose
    public void onClose() {
        try {
            stopPeriodicTask();
            Set<Session> sessionSet = sessionPool.get(traderId + slaveId);
            if (ObjectUtil.isEmpty(sessionSet)) {
                return;
            }
            sessionPool.get(traderId + slaveId).remove(session);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 服务器端推送消息
     */
    public void pushMessage(String traderId, String slaveId, String message) {
        try {
            Set<Session> sessionSet = sessionPool.get(traderId + slaveId);
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

    public Boolean isConnection(String traderId, String slaveId) {
        return sessionPool.containsKey(traderId + slaveId);
    }


    private List<OrderActiveInfoVO> converOrderActive(List<Order> openedOrders, String account) {
        List<OrderActiveInfoVO> collect = new ArrayList<>();
        for (Order o : openedOrders) {
            OrderActiveInfoVO reusableOrderActiveInfoVO = new OrderActiveInfoVO(); // 从对象池中借用对象
            resetOrderActiveInfoVO(reusableOrderActiveInfoVO, o, account); // 重用并重置对象
            collect.add(reusableOrderActiveInfoVO);
        }

        //倒序返回
        return collect.stream()
                .sorted(Comparator.comparing(OrderActiveInfoVO::getOpenTime).reversed())
                .collect(Collectors.toList());
    }


    private void resetOrderActiveInfoVO(OrderActiveInfoVO vo, Order order, String account) {
        vo.setAccount(account);
        vo.setLots(order.Lots);
        vo.setComment(order.Comment);
        vo.setOrderNo(order.Ticket);
        vo.setCommission(order.Commission);
        vo.setSwap(order.Swap);
        vo.setProfit(order.Profit);
        vo.setSymbol(order.Symbol);
        vo.setOpenPrice(order.OpenPrice);
        vo.setMagicNumber(order.MagicNumber);
        vo.setType(order.Type.name());
        //增加五小时
        vo.setOpenTime(DateUtil.toLocalDateTime(DateUtil.offsetHour(DateUtil.date(order.OpenTime), 0)));
        // vo.setOpenTime(order.OpenTime);
        vo.setStopLoss(order.StopLoss);
        vo.setTakeProfit(order.TakeProfit);
    }


    private List<OrderActiveInfoVO> convertOrderActive(List<Order> openedOrders, String account) {
        return openedOrders.stream()
                .map(order -> createOrderActiveInfoVO(order, account))
                .sorted(Comparator.comparing(OrderActiveInfoVO::getOpenTime).reversed())
                .collect(Collectors.toList());
    }

    private OrderActiveInfoVO createOrderActiveInfoVO(Order order, String account) {
        OrderActiveInfoVO vo = new OrderActiveInfoVO();
        vo.setAccount(account);
        vo.setLots(order.Lots);
        vo.setComment(order.Comment);
        vo.setOrderNo(order.Ticket);
        vo.setCommission(order.Commission);
        vo.setSwap(order.Swap);
        vo.setProfit(order.Profit);
        vo.setSymbol(order.Symbol);
        vo.setOpenPrice(order.OpenPrice);
        vo.setMagicNumber(order.MagicNumber);
        vo.setType(order.Type.name());
        // 增加五小时
        vo.setOpenTime(DateUtil.toLocalDateTime(DateUtil.offsetHour(DateUtil.date(order.OpenTime), 0)));
        vo.setStopLoss(order.StopLoss);
        vo.setTakeProfit(order.TakeProfit);
        return vo;
    }
}
