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
import net.maku.followcom.entity.FollowOrderDetailEntity;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.entity.FollowTraderSubscribeEntity;
import net.maku.followcom.enums.ConCodeEnum;
import net.maku.followcom.enums.TraderRepairOrderEnum;
import net.maku.followcom.pojo.EaOrderInfo;
import net.maku.followcom.service.FollowOrderDetailService;
import net.maku.followcom.service.FollowTraderService;
import net.maku.followcom.service.FollowTraderSubscribeService;
import net.maku.followcom.service.impl.FollowOrderDetailServiceImpl;
import net.maku.followcom.service.impl.FollowTraderServiceImpl;
import net.maku.followcom.service.impl.FollowTraderSubscribeServiceImpl;
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
import net.maku.subcontrol.trader.*;
import net.maku.subcontrol.vo.FollowOrderActiveSocketVO;
import net.maku.subcontrol.vo.FollowOrderRepairSocketVO;
import online.mtapi.mt4.Op;
import online.mtapi.mt4.Order;
import online.mtapi.mt4.QuoteClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Component
@ServerEndpoint("/socket/trader/orderRepair/{traderId}/{slaveId}") //此注解相当于设置访问URL
public class TraderOrderRepairWebSocket {

    private static final Logger log = LoggerFactory.getLogger(TraderOrderRepairWebSocket.class);
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
                abstractApiTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(accountId);
                if (ObjectUtil.isNotEmpty(abstractApiTrader)){
                    quoteClient = abstractApiTrader.quoteClient;
                }
            } else {
                abstractApiTrader = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(accountId);
                if (ObjectUtil.isNotEmpty(abstractApiTrader)){
                    quoteClient = abstractApiTrader.quoteClient;
                }
            }
            if (ObjectUtil.isEmpty(quoteClient)) {
                log.info(accountId + "登录异常");
                return;
            }

            //持仓不为空并且为跟单账号 校验漏单信息
            if (!slaveId.equals("0")) {
                Object repair =redisUtil.get(Constant.TRADER_TEMPORARILY_REPAIR+slaveId);
                if (ObjectUtil.isNotEmpty(repair)){
                    pushMessage(traderId, slaveId, JsonUtils.toJsonString(repair));
                    redisUtil.del(Constant.TRADER_TEMPORARILY_REPAIR+slaveId);
                }

                Object o1 = redisCache.get(Constant.TRADER_ACTIVE + accountId);
                FollowOrderRepairSocketVO followOrderRepairSocketVO = new FollowOrderRepairSocketVO();
                List<OrderActiveInfoVO> orderActiveInfoList =new ArrayList<>();
                if (ObjectUtil.isNotEmpty(o1)){
                    orderActiveInfoList = JSONObject.parseArray(o1.toString(), OrderActiveInfoVO.class);
                }
                FollowTraderSubscribeEntity followTraderSubscribe = followTraderSubscribeService.subscription(Long.valueOf(slaveId), Long.valueOf(traderId));
                FollowTraderEntity master = followTraderService.getFollowById(Long.valueOf(traderId));
                FollowTraderEntity slave = followTraderService.getFollowById(Long.valueOf(slaveId));
                Map<Object,Object> sendRepair=redisUtil.hGetAll(Constant.FOLLOW_REPAIR_SEND + FollowConstant.LOCAL_HOST+"#"+slave.getPlatform()+"#"+master.getPlatform()+"#"+followTraderSubscribe.getSlaveAccount()+"#"+followTraderSubscribe.getMasterAccount());
                Map<Object,Object> closeRepair = redisUtil.hGetAll(Constant.FOLLOW_REPAIR_CLOSE + FollowConstant.LOCAL_HOST+"#"+slave.getPlatform()+"#"+master.getPlatform()+"#"+followTraderSubscribe.getSlaveAccount()+"#"+followTraderSubscribe.getMasterAccount());

                List<Object> sendRepairToExtract = new ArrayList<>();

                for (Object repairObj : sendRepair.keySet()) {
                    EaOrderInfo repairComment = (EaOrderInfo) sendRepair.get(repairObj);
                    boolean existsInActive = orderActiveInfoList.stream().anyMatch(order ->String.valueOf(repairComment.getTicket()).equalsIgnoreCase(order.getMagicNumber().toString()));
                    if (!existsInActive) {
                        sendRepairToExtract.add(repairComment);
                    }
                }
                List<Object> closeRepairToRemove = new ArrayList<>();
                List<Object> closeRepairToExtract = new ArrayList<>();
                for (Object repairObj : closeRepair.keySet()) {
                    EaOrderInfo repairComment = (EaOrderInfo) closeRepair.get(repairObj);
                    boolean existsInActive = orderActiveInfoList.stream().anyMatch(order -> String.valueOf(repairComment.getTicket()).equalsIgnoreCase(order.getMagicNumber().toString()));
                    if (!existsInActive) {
                        closeRepairToRemove.add(repairComment);
                    } else {
                        closeRepairToExtract.add(repairComment);
                    }
                }
                String repairKey = Constant.FOLLOW_REPAIR_CLOSE + FollowConstant.LOCAL_HOST + "#" + slave.getPlatform()+ "#" +
                        master.getPlatform()+ "#" + followTraderSubscribe.getSlaveAccount() + "#" + followTraderSubscribe.getMasterAccount();

                redisUtil.pipeline(connection -> {
                    closeRepairToRemove.forEach(ticket -> connection.hDel(repairKey.getBytes(), ticket.toString().getBytes()));
                });

                List<OrderRepairInfoVO> list = Collections.synchronizedList(new ArrayList<>());
                sendRepairToExtract.forEach(o -> {
                    EaOrderInfo eaOrderInfo = (EaOrderInfo) o;
                    OrderRepairInfoVO orderRepairInfoVO = new OrderRepairInfoVO();
                    orderRepairInfoVO.setRepairType(TraderRepairOrderEnum.SEND.getType());
                    orderRepairInfoVO.setMasterLots(eaOrderInfo.getLots());
                    orderRepairInfoVO.setMasterOpenTime(eaOrderInfo.getOpenTime());
                    orderRepairInfoVO.setMasterProfit(eaOrderInfo.getProfit().doubleValue());
                    orderRepairInfoVO.setMasterSymbol(eaOrderInfo.getSymbol());
                    orderRepairInfoVO.setMasterTicket(eaOrderInfo.getTicket());
                    orderRepairInfoVO.setMasterType(Op.forValue(eaOrderInfo.getType()).name());
                    list.add(orderRepairInfoVO);
                });
                closeRepairToExtract.forEach(o -> {
                    EaOrderInfo eaOrderInfo = (EaOrderInfo) o;
                    //通过备注查询未平仓记录
                    List<FollowOrderDetailEntity> detailServiceList = followOrderDetailService.list(new LambdaQueryWrapper<FollowOrderDetailEntity>().eq(FollowOrderDetailEntity::getTraderId, slaveId).isNull(FollowOrderDetailEntity::getCloseTime).eq(FollowOrderDetailEntity::getMagical, ((EaOrderInfo) o).getTicket()));
                    if (ObjectUtil.isNotEmpty(detailServiceList)) {
                        for (FollowOrderDetailEntity detail : detailServiceList) {
                            OrderRepairInfoVO orderRepairInfoVO = new OrderRepairInfoVO();
                            orderRepairInfoVO.setMasterOpenTime(eaOrderInfo.getOpenTime());
                            orderRepairInfoVO.setMasterSymbol(eaOrderInfo.getSymbol());
                            orderRepairInfoVO.setRepairType(TraderRepairOrderEnum.CLOSE.getType());
                            orderRepairInfoVO.setMasterLots(eaOrderInfo.getLots());
                            orderRepairInfoVO.setMasterProfit(ObjectUtil.isNotEmpty(eaOrderInfo.getProfit()) ? eaOrderInfo.getProfit().doubleValue() : 0);
                            orderRepairInfoVO.setMasterType(Op.forValue(eaOrderInfo.getType()).name());
                            orderRepairInfoVO.setMasterTicket(eaOrderInfo.getTicket());
                            orderRepairInfoVO.setSlaveLots(eaOrderInfo.getLots());
                            orderRepairInfoVO.setSlaveType(Op.forValue(eaOrderInfo.getType()).name());
                            orderRepairInfoVO.setSlaveOpenTime(detail.getOpenTime());
                            orderRepairInfoVO.setSlaveSymbol(detail.getSymbol());
                            orderRepairInfoVO.setSlaveTicket(detail.getOrderNo());
                            orderRepairInfoVO.setSlaverProfit(detail.getProfit().doubleValue());
                            list.add(orderRepairInfoVO);
                        }
                    }
                });
                if (list.size()>=2){
                    list.sort((m1, m2) -> m2.getMasterOpenTime().compareTo(m1.getMasterOpenTime()));
                }
                followOrderRepairSocketVO.setOrderRepairInfoVOList(list);
                pushMessage(traderId, slaveId, JsonUtils.toJsonString(followOrderRepairSocketVO));
            }
        } catch (Exception e) {
            log.error("定时推送消息异常", e);
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
}
