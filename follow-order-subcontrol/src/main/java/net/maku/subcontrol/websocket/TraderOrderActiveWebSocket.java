package net.maku.subcontrol.websocket;

import cn.hutool.core.bean.BeanUtil;
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
import net.maku.followcom.enums.TraderStatusEnum;
import net.maku.followcom.pojo.EaOrderInfo;
import net.maku.followcom.service.FollowOrderDetailService;
import net.maku.followcom.service.FollowPlatformService;
import net.maku.followcom.service.FollowTraderService;
import net.maku.followcom.service.FollowTraderSubscribeService;
import net.maku.followcom.service.impl.*;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.followcom.vo.OrderActiveInfoVO;
import net.maku.followcom.vo.OrderRepairInfoVO;
import net.maku.framework.common.cache.RedisCache;
import net.maku.framework.common.cache.RedisUtil;
import net.maku.framework.common.cache.RedissonLockUtil;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.exception.ServerException;
import net.maku.framework.common.utils.JsonUtils;
import net.maku.framework.common.utils.Result;
import net.maku.subcontrol.entity.FollowSubscribeOrderEntity;
import net.maku.subcontrol.enums.TraderRepairOrderEnum;
import net.maku.subcontrol.pojo.OrderActiveInfoVOPool;
import net.maku.subcontrol.service.FollowSubscribeOrderService;
import net.maku.subcontrol.service.impl.FollowSubscribeOrderServiceImpl;
import net.maku.subcontrol.trader.*;
import net.maku.subcontrol.trader.strategy.AbstractOperation;
import net.maku.subcontrol.vo.FollowOrderActiveSocketVO;
import online.mtapi.mt4.Op;
import online.mtapi.mt4.Order;
import online.mtapi.mt4.QuoteClient;
import org.redisson.api.RLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static online.mtapi.mt4.Op.Buy;
import static online.mtapi.mt4.Op.Sell;

@Component
@ServerEndpoint("/socket/trader/orderRepair/{traderId}/{slaveId}") //此注解相当于设置访问URL
public class TraderOrderActiveWebSocket {

    private static final Logger log = LoggerFactory.getLogger(TraderOrderActiveWebSocket.class);
    private final RedissonLockUtil redissonLockUtil=SpringContextUtils.getBean(RedissonLockUtil.class);

    private Session session;

    private String traderId;

    private String slaveId;

    private RedisCache redisCache = SpringContextUtils.getBean(RedisCache.class);

    private static Map<String, Set<Session>> sessionPool = new ConcurrentHashMap<>();
    private RedisUtil redisUtil = SpringContextUtils.getBean(RedisUtil.class);
    private FollowSubscribeOrderService followSubscribeOrderService = SpringContextUtils.getBean(FollowSubscribeOrderServiceImpl.class);
    private LeaderApiTradersAdmin leaderApiTradersAdmin = SpringContextUtils.getBean(LeaderApiTradersAdmin.class);
    private CopierApiTradersAdmin copierApiTradersAdmin = SpringContextUtils.getBean(CopierApiTradersAdmin.class);
    private final OrderActiveInfoVOPool orderActiveInfoVOPool = new OrderActiveInfoVOPool();
    private final List<OrderActiveInfoVO> pendingReturnObjects = new ArrayList<>();
    private FollowTraderSubscribeService followTraderSubscribeService = SpringContextUtils.getBean(FollowTraderSubscribeServiceImpl.class);
    private FollowPlatformService followPlatformService = SpringContextUtils.getBean(FollowPlatformServiceImpl.class);
    private FollowTraderService followTraderService = SpringContextUtils.getBean(FollowTraderServiceImpl.class);
    private ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> scheduledFuture;




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
            RLock lock = redissonLockUtil.getLock("LOCK:" + traderId + ":" + slaveId);
            boolean flag = lock.tryLock(5, TimeUnit.SECONDS);
            if(!flag){return;}
            returnObjectsInBatch();
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
                followTraderEntity = followTraderService.getById(Long.valueOf(accountId));
                abstractApiTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(accountId);
                if (ObjectUtil.isEmpty(abstractApiTrader) || ObjectUtil.isEmpty(abstractApiTrader.quoteClient)
                        || !abstractApiTrader.quoteClient.Connected()) {
                    leaderApiTradersAdmin.removeTrader(accountId);
                    ConCodeEnum conCodeEnum = leaderApiTradersAdmin.addTrader(followTraderEntity);
                    if (conCodeEnum == ConCodeEnum.SUCCESS) {
                        quoteClient = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(followTraderEntity.getId().toString()).quoteClient;
                        LeaderApiTrader leaderApiTrader1 = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(followTraderEntity.getId().toString());
                        leaderApiTrader1.startTrade();
                    }
                } else {
                    quoteClient = abstractApiTrader.quoteClient;
                }
            } else {
                followTraderEntity = followTraderService.getById(Long.valueOf(accountId));
                abstractApiTrader = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(accountId);
                if (ObjectUtil.isEmpty(abstractApiTrader) || ObjectUtil.isEmpty(abstractApiTrader.quoteClient)
                        || !abstractApiTrader.quoteClient.Connected()) {
                    copierApiTradersAdmin.removeTrader(accountId);
                    ConCodeEnum conCodeEnum = copierApiTradersAdmin.addTrader(followTraderEntity);
                    if (conCodeEnum == ConCodeEnum.SUCCESS) {
                        quoteClient = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(followTraderEntity.getId().toString()).quoteClient;
                        CopierApiTrader copierApiTrader = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(followTraderEntity.getId().toString());
                        copierApiTrader.startTrade();
                    }
                } else {
                    quoteClient = abstractApiTrader.quoteClient;
                }
            }
            if (ObjectUtil.isEmpty(quoteClient)) {
                throw new ServerException(accountId + "登录异常");
            }
            quoteClient = leaderApiTradersAdmin.quoteClientMap.get(accountId);
            if(quoteClient==null) {
                FollowPlatformEntity followPlatformServiceOne = followPlatformService.getOne(new LambdaQueryWrapper<FollowPlatformEntity>().eq(FollowPlatformEntity::getServer, followTraderEntity.getPlatform()));
                String serverNode = followPlatformServiceOne.getServerNode();
                String[] split = serverNode.split(":");
                quoteClient = new QuoteClient(Integer.parseInt(followTraderEntity.getAccount()), followTraderEntity.getPassword(), split[0], Integer.valueOf(split[1]));
                quoteClient.Connect();
                leaderApiTradersAdmin.quoteClientMap.put(accountId,quoteClient);
            }
            //所有持仓
            List<Order> openedOrders = Arrays.stream(quoteClient.GetOpenedOrders()).filter(order -> order.Type == Buy || order.Type == Sell).collect(Collectors.toList());

            log.info("{}-MT4,订单数量{},持仓数据：{}",accountId,openedOrders.size(),openedOrders);
            List<OrderActiveInfoVO> orderActiveInfoList = converOrderActive(openedOrders, abstractApiTrader.getTrader().getAccount());
            FollowOrderActiveSocketVO followOrderActiveSocketVO = new FollowOrderActiveSocketVO();
            followOrderActiveSocketVO.setOrderActiveInfoList(orderActiveInfoList);
            //存入redis

            redisCache.set(Constant.TRADER_ACTIVE + accountId, JSONObject.toJSON(orderActiveInfoList));

            //持仓不为空并且为跟单账号 校验漏单信息
            if (!slaveId.equals("0")) {
                log.info("follow sub" + slaveId + ":" + traderId);
                FollowTraderSubscribeEntity followTraderSubscribe = followTraderSubscribeService.subscription(Long.valueOf(slaveId), Long.valueOf(traderId));

                List<Object> sendRepair = redisUtil.lGet(Constant.FOLLOW_REPAIR_SEND + followTraderSubscribe.getId(), 0, -1);
                List<Object> closeRepair = redisUtil.lGet(Constant.FOLLOW_REPAIR_CLOSE + followTraderSubscribe.getId(), 0, -1);
                // 数据处理逻辑（如前面的代码示例）
                List<Object> sendRepairToRemove = new ArrayList<>();
                List<Object> sendRepairToExtract = new ArrayList<>();

                for (Object repairObj : sendRepair) {
                    EaOrderInfo repairComment = (EaOrderInfo) repairObj;
                    boolean existsInActive = orderActiveInfoList.stream().anyMatch(order -> repairComment.getSlaveComment().equalsIgnoreCase(order.getComment()));
                    if (existsInActive) {
                        sendRepairToRemove.add(repairObj);
                    } else {
                        sendRepairToExtract.add(repairObj);
                    }
                }
                sendRepairToRemove.forEach(repair -> redisUtil.lRemove(Constant.FOLLOW_REPAIR_SEND + followTraderSubscribe.getId(), 1, repair));

                List<Object> closeRepairToRemove = new ArrayList<>();
                List<Object> closeRepairToExtract = new ArrayList<>();
                for (Object repairObj : closeRepair) {
                    EaOrderInfo repairComment = (EaOrderInfo) repairObj;
                    boolean existsInActive = orderActiveInfoList.stream().anyMatch(order -> repairComment.getSlaveComment().equalsIgnoreCase(order.getComment()));
                    if (!existsInActive) {
                        closeRepairToRemove.add(repairObj);
                    } else {
                        closeRepairToExtract.add(repairObj);
                    }
                }
                closeRepairToRemove.forEach(repair -> redisUtil.lRemove(Constant.FOLLOW_REPAIR_CLOSE + followTraderSubscribe.getId(), 1, repair));

                List<OrderRepairInfoVO> list = new ArrayList<>();
                sendRepairToExtract.parallelStream().forEach(o -> {
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
                closeRepairToExtract.parallelStream().forEach(o -> {
                    //通过备注查询未平仓记录
                    FollowSubscribeOrderEntity detailServiceOne = followSubscribeOrderService.getOne(new LambdaQueryWrapper<FollowSubscribeOrderEntity>().eq(FollowSubscribeOrderEntity::getSlaveId, slaveId).eq(FollowSubscribeOrderEntity::getSlaveComment, ((EaOrderInfo) o).getSlaveComment()));
                    if (ObjectUtil.isNotEmpty(detailServiceOne)) {
                        OrderRepairInfoVO orderRepairInfoVO = new OrderRepairInfoVO();
                        BeanUtil.copyProperties(detailServiceOne, orderRepairInfoVO);
                        orderRepairInfoVO.setRepairType(TraderRepairOrderEnum.CLOSE.getType());
                        orderRepairInfoVO.setMasterLots(detailServiceOne.getMasterLots().doubleValue());
                        orderRepairInfoVO.setMasterProfit(ObjectUtil.isNotEmpty(detailServiceOne.getMasterProfit()) ? detailServiceOne.getMasterProfit().doubleValue() : 0);
                        orderRepairInfoVO.setMasterType(Op.forValue(detailServiceOne.getMasterType()).name());
                        orderRepairInfoVO.setSlaveLots(detailServiceOne.getSlaveLots().doubleValue());
                        orderRepairInfoVO.setSlaveType(Op.forValue(detailServiceOne.getSlaveType()).name());
                        list.add(orderRepairInfoVO);
                    }
                });
                list.sort((m1, m2) -> m2.getMasterOpenTime().compareTo(m1.getMasterOpenTime()));
                followOrderActiveSocketVO.setOrderRepairInfoVOList(list);
            }
            pushMessage(traderId, slaveId, JsonUtils.toJsonString(followOrderActiveSocketVO));
        } catch (Exception e) {
            log.error("定时推送消息异常", e);
        }finally {
            redissonLockUtil.unlock("LOCK:" + traderId + ":" + slaveId);
        }
    }

    private void stopPeriodicTask() {
        if (scheduledFuture != null && !scheduledFuture.isCancelled()) {
            scheduledFuture.cancel(true);
        }
    }

    @OnClose
    public void onClose() {
        try {
            stopPeriodicTask();
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

    private void returnObjectsInBatch() {
        synchronized (pendingReturnObjects) {
            if (pendingReturnObjects.isEmpty()) {
                return; // 如果没有待归还的对象，直接返回
            }

            // 归还所有对象
            for (OrderActiveInfoVO vo : pendingReturnObjects) {
                orderActiveInfoVOPool.returnObject(vo);
            }

            // 清空待归还列表
            pendingReturnObjects.clear();
        }
    }
}
