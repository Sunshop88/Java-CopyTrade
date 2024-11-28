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
import net.maku.followcom.entity.FollowTraderSubscribeEntity;
import net.maku.followcom.pojo.EaOrderInfo;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.followcom.vo.OrderActiveInfoVO;
import net.maku.followcom.vo.OrderRepairInfoVO;
import net.maku.framework.common.cache.RedisCache;
import net.maku.framework.common.cache.RedisUtil;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.utils.JsonUtils;
import net.maku.subcontrol.entity.FollowSubscribeOrderEntity;
import net.maku.subcontrol.enums.TraderRepairOrderEnum;
import net.maku.subcontrol.pojo.OrderActiveInfoVOPool;
import net.maku.subcontrol.service.FollowSubscribeOrderService;
import net.maku.subcontrol.service.impl.FollowSubscribeOrderServiceImpl;
import net.maku.subcontrol.trader.AbstractApiTrader;
import net.maku.subcontrol.trader.CopierApiTradersAdmin;
import net.maku.subcontrol.trader.LeaderApiTradersAdmin;
import net.maku.subcontrol.vo.FollowOrderActiveSocketVO;
import online.mtapi.mt4.Op;
import online.mtapi.mt4.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static online.mtapi.mt4.Op.Buy;
import static online.mtapi.mt4.Op.Sell;

@Component
@ServerEndpoint("/socket/trader/orderRepair/{traderId}/{slaveId}") //此注解相当于设置访问URL
public class TraderOrderActiveWebSocket {

    private static final Logger log = LoggerFactory.getLogger(TraderOrderActiveWebSocket.class);
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


    @OnOpen
    public void onOpen(Session session, @PathParam(value = "traderId") String traderId, @PathParam(value = "slaveId") String slaveId) {
        try {
            this.session = session;
            this.traderId = traderId;
            this.slaveId = slaveId;
            Set<Session> sessionSet = sessionPool.getOrDefault(traderId + slaveId, ConcurrentHashMap.newKeySet());
            sessionSet.add(session);
            sessionPool.put(traderId + slaveId, sessionSet);
            sendPeriodicMessage(traderId, slaveId);
        } catch (Exception e) {
            log.info("连接异常" + e);
            onClose();
            throw new RuntimeException(e);
        }
    }

    public void sendPeriodicMessage(String traderId, String slaveId) {
        try {
            returnObjectsInBatch();
            Set<Session> sessionSet = sessionPool.get(traderId + slaveId);
            if (ObjectUtil.isEmpty(sessionSet)) {
                return;
            }
            String accountId = slaveId;
            if (slaveId.equals("0")) {
                //喊单
                accountId = traderId;
            }
            AbstractApiTrader leaderApiTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(accountId);
            if (ObjectUtil.isEmpty(leaderApiTrader)) {
                leaderApiTrader = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(accountId);
            }
            //所有持仓
            List<Order> openedOrders = Arrays.stream(leaderApiTrader.quoteClient.GetOpenedOrders()).filter(order -> order.Type == Buy || order.Type == Sell).collect(Collectors.toList());
            List<OrderActiveInfoVO> orderActiveInfoList = converOrderActive(openedOrders, leaderApiTrader.getTrader().getAccount());
            FollowOrderActiveSocketVO followOrderActiveSocketVO = new FollowOrderActiveSocketVO();
            followOrderActiveSocketVO.setOrderActiveInfoList(orderActiveInfoList);
            //存入redis
            redisCache.set(Constant.TRADER_ACTIVE + accountId, JSONObject.toJSON(orderActiveInfoList));

            //持仓不为空并且为跟单账号 校验漏单信息
            if (!slaveId.equals("0")) {
                FollowTraderSubscribeEntity followTraderSubscribe = (FollowTraderSubscribeEntity) redisUtil.hGet(Constant.FOLLOW_SUB_TRADER + slaveId, traderId);

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
                    FollowSubscribeOrderEntity detailServiceOne = followSubscribeOrderService.getOne(new LambdaQueryWrapper<FollowSubscribeOrderEntity>().eq(FollowSubscribeOrderEntity::getSlaveComment, ((EaOrderInfo) o).getSlaveComment()));
                    if (ObjectUtil.isNotEmpty(detailServiceOne)) {
                        OrderRepairInfoVO orderRepairInfoVO = new OrderRepairInfoVO();
                        BeanUtil.copyProperties(detailServiceOne, orderRepairInfoVO);
                        orderRepairInfoVO.setRepairType(TraderRepairOrderEnum.CLOSE.getType());
                        orderRepairInfoVO.setMasterLots(detailServiceOne.getMasterLots().doubleValue());
                        orderRepairInfoVO.setMasterProfit(detailServiceOne.getMasterProfit().doubleValue());
                        orderRepairInfoVO.setMasterType(Op.forValue(detailServiceOne.getMasterType()).name());
                        orderRepairInfoVO.setSlaveLots(detailServiceOne.getSlaveLots().doubleValue());
                        orderRepairInfoVO.setSlaveType(Op.forValue(detailServiceOne.getSlaveType()).name());
                        list.add(orderRepairInfoVO);
                    }
                });
                followOrderActiveSocketVO.setOrderRepairInfoVOList(list);
            }
            pushMessage(traderId, slaveId, JsonUtils.toJsonString(followOrderActiveSocketVO));
        } catch (Exception e) {
            log.error("定时推送消息异常", e);
        }
    }


    @OnClose
    public void onClose() {
        try {
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
            OrderActiveInfoVO reusableOrderActiveInfoVO = orderActiveInfoVOPool.borrowObject(); // 从对象池中借用对象
            resetOrderActiveInfoVO(reusableOrderActiveInfoVO, o, account); // 重用并重置对象
            collect.add(reusableOrderActiveInfoVO);
        }
        // 将所有借用的对象添加到待归还列表中
        synchronized (pendingReturnObjects) {
            pendingReturnObjects.addAll(collect);
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
