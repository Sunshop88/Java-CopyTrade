package net.maku.mascontrol.websocket;

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
import net.maku.followcom.entity.FollowVpsEntity;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.enums.TraderRepairOrderEnum;
import net.maku.followcom.enums.TraderTypeEnum;
import net.maku.followcom.pojo.EaOrderInfo;
import net.maku.followcom.service.FollowOrderDetailService;
import net.maku.followcom.service.FollowTraderService;
import net.maku.followcom.service.FollowTraderSubscribeService;
import net.maku.followcom.service.FollowVpsService;
import net.maku.followcom.service.impl.FollowOrderDetailServiceImpl;
import net.maku.followcom.service.impl.FollowTraderServiceImpl;
import net.maku.followcom.service.impl.FollowTraderSubscribeServiceImpl;
import net.maku.followcom.service.impl.FollowVpsServiceImpl;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.followcom.vo.OrderActiveInfoVO;
import net.maku.followcom.vo.OrderRepairInfoVO;
import net.maku.framework.common.cache.RedisCache;
import net.maku.framework.common.cache.RedisUtil;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.utils.JsonUtils;
import net.maku.mascontrol.vo.MasterRepairVO;
import net.maku.mascontrol.vo.RepairDataVo;
import net.maku.mascontrol.vo.RepairVpsVO;
import online.mtapi.mt4.Op;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

@Component
@ServerEndpoint("/socket/trader/repair/{vpsId}/{masterAccount}/{slaveAccount}") //此注解相当于设置访问URL
public class TraderRepairManageWebSocket {

    private static final Logger log = LoggerFactory.getLogger(TraderRepairManageWebSocket.class);
    private Session session;
    private Integer vpsId;
    private Integer masterAccount;
    private Integer slaveAccount;
    private static Map<String, Set<Session>> sessionPool = new ConcurrentHashMap<>();

    private RedisUtil redisUtil = SpringContextUtils.getBean(RedisUtil.class);
    private FollowTraderSubscribeService followTraderSubscribeService = SpringContextUtils.getBean(FollowTraderSubscribeServiceImpl.class);
    private FollowTraderService followTraderService = SpringContextUtils.getBean(FollowTraderServiceImpl.class);
    private ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> scheduledFuture;
    private FollowOrderDetailService followOrderDetailService = SpringContextUtils.getBean(FollowOrderDetailServiceImpl.class);
    private FollowVpsService followVpsService = SpringContextUtils.getBean(FollowVpsServiceImpl.class);
    private RedisCache redisCache = SpringContextUtils.getBean(RedisCache.class);


    @OnOpen
    public void onOpen(Session session,@PathParam(value = "vpsId") Integer vpsId,@PathParam(value = "masterAccount") Integer masterAccount,@PathParam(value = "slaveAccount") Integer slaveAccount) {
        try {
            this.session = session;
            this.vpsId = vpsId;
            this.masterAccount=masterAccount;
            this.slaveAccount=slaveAccount;
            Set<Session> sessionSet = sessionPool.getOrDefault(vpsId+masterAccount+slaveAccount+"", ConcurrentHashMap.newKeySet());
            sessionSet.add(session);
            sessionPool.put(vpsId+masterAccount+slaveAccount+"", sessionSet);
            //开启定时任务
            this.scheduledFuture = scheduledExecutorService.scheduleAtFixedRate(() -> {
                try {
                    sendPeriodicMessage();
                } catch (Exception e) {
                    log.info("WebSocket建立连接异常" + e);
                }
            }, 0, 5, TimeUnit.SECONDS);

        } catch (Exception e) {
            log.info("连接异常" + e);
            throw new RuntimeException(e);
        }
    }

    public void sendPeriodicMessage() {
        List<FollowVpsEntity> followVpsEntityList;
        if (vpsId!=0){
            followVpsEntityList= Collections.singletonList(followVpsService.getById(vpsId));
        }else {
            followVpsEntityList= followVpsService.list(new LambdaQueryWrapper<FollowVpsEntity>().eq(FollowVpsEntity::getDeleted, CloseOrOpenEnum.CLOSE.getValue()));
        }
        RepairDataVo repairDataVo=RepairDataVo.builder().build();
        //总漏单数量
        AtomicReference<Integer> total= new AtomicReference<>(0);
        //漏单VPS
        repairDataVo.setVpsNum(followVpsEntityList.size());
        //漏单信号源
        AtomicReference<Integer> masterNum= new AtomicReference<>(0);
        //漏单跟单账号
        AtomicReference<Integer> slaveNum= new AtomicReference<>(0);
        List<RepairVpsVO> repairVpsVOList = new ArrayList<>(List.of());
        followVpsEntityList.forEach(o->{
            RepairVpsVO repairVpsVO= RepairVpsVO.builder().build();
            repairVpsVO.setVpsName(o.getName());
            List<MasterRepairVO> masterRepairVOList=new ArrayList<>(List.of());
            List<FollowTraderEntity> list ;
            if (masterAccount!=0){
                list=followTraderService.list(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getType, TraderTypeEnum.MASTER_REAL.getType()).like(FollowTraderEntity::getAccount,masterAccount).eq(FollowTraderEntity::getIpAddr, o.getIpAddress()));
            }else{
                list=followTraderService.list(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getType, TraderTypeEnum.MASTER_REAL.getType()).eq(FollowTraderEntity::getIpAddr, o.getIpAddress()));
            }
            //遍历账号信息
            //vps 漏单数量
            AtomicReference<Integer> num= new AtomicReference<>(0);
            list.forEach(trader->{
                MasterRepairVO masterRepairVO = MasterRepairVO.builder().build();
                masterRepairVO.setMasterAccount(Integer.valueOf(trader.getAccount()));
                masterRepairVO.setMasterPlatform(trader.getPlatform());
                //查询信号源下跟单的漏单信息
                List<OrderRepairInfoVO> orderRepairInfoVOList = Collections.synchronizedList(new ArrayList<>());/*
             Map<String, Map<Object, Object>> sendMap = redisUtil.getKeysByThreeConditions("follow:repair:send:*", o.getIpAddress(), trader.getPlatform(), trader.getAccount());
              Map<String, Map<Object, Object>> closeMap = redisUtil.getKeysByThreeConditions("follow:repair:close:*", o.getIpAddress(), trader.getPlatform(), trader.getAccount());
              Integer salvenum = getOrder(orderRepairInfoVOList, sendMap, closeMap, trader.getId(),o.getIpAddress());*/
                if (slaveAccount!=0) {

                }
              Map<Object, Object> objectObjectMap = redisCache.hGetStrAll(Constant.REPAIR_SEND+trader.getAccount());
                if(objectObjectMap!=null){
                    objectObjectMap.values().forEach(obj->{
                        JSONObject jsonObject = JSONObject.parseObject(obj.toString());
                        Collection<Object> values = jsonObject.values();
                        values.forEach(vs->{
                                OrderRepairInfoVO infoVO = JSONObject.parseObject(vs.toString(), OrderRepairInfoVO.class);
                            if (slaveAccount!=0) {
                                if( infoVO.getSlaveAccount().contains("slaveAccount")){
                                    orderRepairInfoVOList.add(infoVO);
                                }
                            }else{
                                orderRepairInfoVOList.add(infoVO);
                            }

                        });
                       
                    });
                }

                Map<Object, Object> closeMap = redisCache.hGetStrAll(Constant.REPAIR_CLOSE+trader.getAccount());
                if(closeMap!=null){
                    closeMap.values().forEach(obj->{
                        JSONObject jsonObject = JSONObject.parseObject(obj.toString());
                        Collection<Object> values = jsonObject.values();
                        values.forEach(vs->{
                            OrderRepairInfoVO infoVO = JSONObject.parseObject(vs.toString(), OrderRepairInfoVO.class);
                            if (slaveAccount!=0) {
                                if( infoVO.getSlaveAccount().contains("slaveAccount")){
                                    orderRepairInfoVOList.add(infoVO);
                                }
                            }else{
                                orderRepairInfoVOList.add(infoVO);
                            }

                        });

                    });
                }

               // slaveNum.updateAndGet(v -> v + salvenum);
                slaveNum.updateAndGet(v -> v + objectObjectMap.size());
                log.info(trader.getAccount()+"漏单"+orderRepairInfoVOList.size());
                if (!orderRepairInfoVOList.isEmpty()){
                    //排序
                    if (orderRepairInfoVOList.size()>=2){
                        orderRepairInfoVOList.sort((m1, m2) -> m2.getMasterOpenTime().compareTo(m1.getMasterOpenTime()));
                    }
                    masterRepairVO.setRepairNum(orderRepairInfoVOList.size());
                    masterRepairVO.setPageData(orderRepairInfoVOList);
                    masterRepairVOList.add(masterRepairVO);
                    num.updateAndGet(v -> v + orderRepairInfoVOList.size());
                    masterNum.updateAndGet(v -> v + 1);
                }
            });
            repairVpsVO.setPageData(masterRepairVOList);
            repairVpsVO.setRepairNum(num.get());
            repairVpsVOList.add(repairVpsVO);
            //总数
            total.updateAndGet(v -> v + num.get());
        });
        repairDataVo.setMasterNum(masterNum.get());
        repairDataVo.setSlaveNum(slaveNum.get());
        repairDataVo.setPageData(repairVpsVOList);
        repairDataVo.setTotal(total.get());
        pushMessage(JsonUtils.toJsonString(repairDataVo));
    }

    private Integer getOrder(List<OrderRepairInfoVO> orderRepairInfoVOList, Map<String, Map<Object, Object>> sendRepair, Map<String, Map<Object, Object>> closeRepair,Long accountId,String ipAddr) {
        //跟单账户数量
        AtomicReference<Integer> num= new AtomicReference<>(0);
        //查看跟单
        List<FollowTraderSubscribeEntity> subscribeOrder = followTraderSubscribeService.getSubscribeOrder(accountId);
        if (slaveAccount!=0) {
            subscribeOrder= subscribeOrder.stream().filter(o->o.getSlaveAccount().equals(slaveAccount)).toList();
        }
        subscribeOrder.forEach(o->{
            AtomicReference<Integer> flag= new AtomicReference<>(0);
            FollowTraderEntity slave = followTraderService.getFollowById(o.getSlaveId());
            //需为在线状态
            if (slave.getStatus().equals(CloseOrOpenEnum.OPEN.getValue()))return;
            FollowTraderEntity master = followTraderService.getFollowById(accountId);
            Map<Object, Object> sendmap = sendRepair.get(Constant.FOLLOW_REPAIR_SEND + ipAddr + "#" + slave.getPlatform() + "#" + master.getPlatform() + "#" + o.getSlaveAccount() + "#" + o.getMasterAccount());
            if (ObjectUtil.isEmpty(sendmap))return;
            Object o1 = redisUtil.get(Constant.TRADER_ACTIVE + o.getSlaveId());
            List<OrderActiveInfoVO> orderActiveInfoList;
            if (ObjectUtil.isNotEmpty(o1)){
                orderActiveInfoList = JSONObject.parseArray(o1.toString(), OrderActiveInfoVO.class);
                //补单
                for (Object repairObj : sendmap.keySet()) {
                    try {
                    EaOrderInfo eaOrderInfo = (EaOrderInfo) sendmap.get(repairObj);
                    boolean existsInActive = orderActiveInfoList.stream().anyMatch(order ->String.valueOf(eaOrderInfo.getTicket()).equalsIgnoreCase(order.getMagicNumber().toString()));
                    if (!existsInActive) {
                        OrderRepairInfoVO orderRepairInfoVO = new OrderRepairInfoVO();
                        orderRepairInfoVO.setRepairType(TraderRepairOrderEnum.SEND.getType());
                        orderRepairInfoVO.setMasterLots(eaOrderInfo.getLots());
                        orderRepairInfoVO.setMasterOpenTime(eaOrderInfo.getOpenTime());
                        orderRepairInfoVO.setMasterProfit(eaOrderInfo.getProfit().doubleValue());
                        orderRepairInfoVO.setMasterSymbol(eaOrderInfo.getSymbol());
                        orderRepairInfoVO.setMasterTicket(eaOrderInfo.getTicket());
                        orderRepairInfoVO.setMasterType(Op.forValue(eaOrderInfo.getType()).name());
                        orderRepairInfoVO.setMasterId(eaOrderInfo.getMasterId());
                        orderRepairInfoVOList.add(orderRepairInfoVO);
                        if (flag.get() ==0){
                            flag.set(1);
                            num.updateAndGet(v -> v + 1);
                        }
                    }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                //补仓
                Map<Object, Object> closemap = closeRepair.get(Constant.FOLLOW_REPAIR_CLOSE+ ipAddr + "#" + slave.getPlatform() + "#" + master.getPlatform() + "#" + o.getSlaveAccount() + "#" + o.getMasterAccount());
                if (ObjectUtil.isEmpty(closemap))return;
                for (Object repairObj : closemap.keySet()) {
                    try {
                    EaOrderInfo eaOrderInfo = (EaOrderInfo) closemap.get(repairObj);
                    boolean existsInActive = orderActiveInfoList.stream().anyMatch(order -> String.valueOf(eaOrderInfo.getTicket()).equalsIgnoreCase(order.getMagicNumber().toString()));
                    if (existsInActive) {
                        //通过备注查询未平仓记录
                        List<FollowOrderDetailEntity> detailServiceList = followOrderDetailService.list(new LambdaQueryWrapper<FollowOrderDetailEntity>().eq(FollowOrderDetailEntity::getTraderId, o.getSlaveId()).eq(FollowOrderDetailEntity::getMagical, eaOrderInfo.getTicket()));
                        if (ObjectUtil.isNotEmpty(detailServiceList)) {
                            detailServiceList.forEach(detail -> {
                                OrderRepairInfoVO orderRepairInfoVO = new OrderRepairInfoVO();
                                orderRepairInfoVO.setMasterOpenTime(eaOrderInfo.getOpenTime());
                                orderRepairInfoVO.setMasterCloseTime(eaOrderInfo.getCloseTime());
                                orderRepairInfoVO.setMasterSymbol(eaOrderInfo.getSymbol());
                                orderRepairInfoVO.setMasterOpenPrice(eaOrderInfo.getOpenPrice());
                                orderRepairInfoVO.setRepairType(TraderRepairOrderEnum.CLOSE.getType());
                                orderRepairInfoVO.setMasterLots(eaOrderInfo.getLots());
                                orderRepairInfoVO.setMasterProfit(ObjectUtil.isNotEmpty(eaOrderInfo.getProfit()) ? eaOrderInfo.getProfit().doubleValue() : 0);
                                orderRepairInfoVO.setMasterType(Op.forValue(eaOrderInfo.getType()).name());
                                orderRepairInfoVO.setMasterTicket(eaOrderInfo.getTicket());
                                orderRepairInfoVO.setSlaveLots(eaOrderInfo.getLots());
                                orderRepairInfoVO.setSlaveType(Op.forValue(eaOrderInfo.getType()).name());
                                orderRepairInfoVO.setSlaveOpenTime(detail.getOpenTime());
                                orderRepairInfoVO.setSlaveOpenPrice(eaOrderInfo.getOpenPrice());
                                orderRepairInfoVO.setSlaveCloseTime(detail.getCloseTime());
                                orderRepairInfoVO.setSlaveSymbol(detail.getSymbol());
                                orderRepairInfoVO.setSlaveAccount(detail.getAccount());
                                orderRepairInfoVO.setSlavePlatform(detail.getPlatform());
                                orderRepairInfoVO.setSlaveTicket(detail.getOrderNo());
                                orderRepairInfoVO.setSlaverProfit(detail.getProfit().doubleValue());
                                orderRepairInfoVO.setMasterId(eaOrderInfo.getMasterId());
                                orderRepairInfoVO.setSlaveId(detail.getTraderId());
                                orderRepairInfoVOList.add(orderRepairInfoVO);
                                if (flag.get() == 0) {
                                    flag.set(1);
                                    num.updateAndGet(v -> v + 1);
                                }
                            });
                        }
                    }
                } catch (Exception e) {
                       e.printStackTrace();
                    }
                }
            }
        });
        return num.get();
    }

    public void pushMessage(String message) {
        try {
            Set<Session> sessionSet = sessionPool.get(vpsId+masterAccount+slaveAccount+"");
            if (ObjectUtil.isEmpty(sessionSet)) {
                return;
            }
            if (session.isOpen()) {
                synchronized (session) {
                    session.getBasicRemote().sendText(message);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void stopPeriodicTask() {
        if (scheduledFuture != null && !scheduledFuture.isCancelled()) {
            scheduledFuture.cancel(true);
        }
    }

    @OnMessage
    public void onMessage(String message) {
    }

    @OnClose
    public void onClose() {
        stopPeriodicTask();
        if(sessionPool.get(vpsId+masterAccount+slaveAccount+"")!=null){
            sessionPool.get(vpsId+masterAccount+slaveAccount+"").remove(session);
        }
    }

}
