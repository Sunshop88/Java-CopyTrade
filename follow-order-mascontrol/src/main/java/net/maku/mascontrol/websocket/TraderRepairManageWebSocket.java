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
import net.maku.followcom.entity.*;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.enums.TraderRepairOrderEnum;
import net.maku.followcom.enums.TraderTypeEnum;
import net.maku.followcom.pojo.EaOrderInfo;
import net.maku.followcom.service.*;
import net.maku.followcom.service.impl.*;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.followcom.vo.OrderActiveInfoVO;
import net.maku.followcom.vo.OrderRepairInfoVO;
import net.maku.followcom.vo.VpsUserVO;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@ServerEndpoint("/socket/trader/repair/{vpsId}/{masterAccount}/{slaveAccount}/{userId}") //此注解相当于设置访问URL
public class TraderRepairManageWebSocket {

    private static final Logger log = LoggerFactory.getLogger(TraderRepairManageWebSocket.class);
    private Session session;
    private String vpsId;
    private Integer masterAccount;
    private Integer slaveAccount;
    private Long userId;
    private static Map<String, Set<Session>> sessionPool = new ConcurrentHashMap<>();

    private RedisUtil redisUtil = SpringContextUtils.getBean(RedisUtil.class);
    private FollowTraderSubscribeService followTraderSubscribeService = SpringContextUtils.getBean(FollowTraderSubscribeServiceImpl.class);
    private FollowTraderService followTraderService = SpringContextUtils.getBean(FollowTraderServiceImpl.class);
    private ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> scheduledFuture;
    private FollowOrderDetailService followOrderDetailService = SpringContextUtils.getBean(FollowOrderDetailServiceImpl.class);
    private FollowVpsService followVpsService = SpringContextUtils.getBean(FollowVpsServiceImpl.class);
    private RedisCache redisCache = SpringContextUtils.getBean(RedisCache.class);
    private FollowVpsUserService followVpsUserService=SpringContextUtils.getBean(FollowVpsUserServiceImpl.class);


    @OnOpen
    public void onOpen(Session session,@PathParam(value = "vpsId") String vpsId,@PathParam(value = "masterAccount") Integer masterAccount,@PathParam(value = "slaveAccount") Integer slaveAccount,@PathParam(value = "userId") Long userId) {
        try {
            this.session = session;
            this.vpsId = vpsId;
            this.masterAccount=masterAccount;
            this.slaveAccount=slaveAccount;
            this.userId = userId;
            Set<Session> sessionSet = sessionPool.getOrDefault(vpsId+masterAccount+slaveAccount+userId+"", ConcurrentHashMap.newKeySet());
            sessionSet.add(session);
            sessionPool.put(vpsId+masterAccount+slaveAccount+userId+"", sessionSet);
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
        //除了admin都需要判断
        //查看当前用户拥有的vps
        List<VpsUserVO> vpsList = new ArrayList<>();
        if (!ObjectUtil.equals(Objects.requireNonNull(userId).toString(), "10000")) {
                List<FollowVpsUserEntity> vpsUserEntityList = followVpsUserService.list(new LambdaQueryWrapper<FollowVpsUserEntity>().eq(FollowVpsUserEntity::getUserId,userId).eq(FollowVpsUserEntity::getDeleted,CloseOrOpenEnum.CLOSE.getValue()));
                if(ObjectUtil.isNotEmpty(vpsUserEntityList)){
                    for (FollowVpsUserEntity followVpsUserEntity : vpsUserEntityList) {
                        VpsUserVO vpsUserVO = new VpsUserVO();
                        vpsUserVO.setName(followVpsUserEntity.getVpsName());
                        vpsUserVO.setId(followVpsUserEntity.getVpsId());
                        vpsList.add(vpsUserVO);
                    }
                }


        }else{
            List<FollowVpsEntity> list = followVpsService.list(new LambdaQueryWrapper<FollowVpsEntity>().eq(FollowVpsEntity::getDeleted, CloseOrOpenEnum.CLOSE.getValue()).eq(FollowVpsEntity::getIsActive,CloseOrOpenEnum.OPEN.getValue()).eq(FollowVpsEntity::getIsMonitorRepair,CloseOrOpenEnum.OPEN.getValue()));
            if(ObjectUtil.isNotEmpty(list)){
                for (FollowVpsEntity vs : list) {
                    VpsUserVO vpsUserVO = new VpsUserVO();
                    vpsUserVO.setName(vs.getName());
                    vpsUserVO.setId(vs.getId());
                    vpsList.add(vpsUserVO);
                }
            }
        }

        List<Integer> vpsIds = vpsList.stream().map(VpsUserVO::getId).toList();
        if (!vpsId.equals("0")){
            String[] split = vpsId.split("-");
            List<Integer> list=new ArrayList<>();
            for (int i = 0; i < split.length; i++) {
                if(vpsIds.contains(Integer.valueOf(split[i]))){
                    list.add(Integer.valueOf(split[i]));
                }
            }
            followVpsEntityList= followVpsService.list(new LambdaQueryWrapper<FollowVpsEntity>().in(FollowVpsEntity::getId,list).eq(FollowVpsEntity::getDeleted,CloseOrOpenEnum.CLOSE.getValue()).eq(FollowVpsEntity::getIsActive,CloseOrOpenEnum.OPEN.getValue()).eq(FollowVpsEntity::getIsMonitorRepair,CloseOrOpenEnum.OPEN.getValue()));
        }else {

            if(ObjectUtil.isEmpty(vpsList)){
                followVpsEntityList=new ArrayList<>();
            }else{
                followVpsEntityList=followVpsService.list(new LambdaQueryWrapper<FollowVpsEntity>().in(FollowVpsEntity::getId,vpsIds).eq(FollowVpsEntity::getDeleted,CloseOrOpenEnum.CLOSE.getValue()).eq(FollowVpsEntity::getIsActive,CloseOrOpenEnum.OPEN.getValue()).eq(FollowVpsEntity::getIsMonitorRepair,CloseOrOpenEnum.OPEN.getValue()));
            }
        
        }
        RepairDataVo repairDataVo=RepairDataVo.builder().build();
        //总漏单数量
        AtomicReference<Integer> total= new AtomicReference<>(0);
        //漏单VPS
        repairDataVo.setVpsNum(Long.valueOf(followVpsService.count(new LambdaQueryWrapper<FollowVpsEntity>().eq(FollowVpsEntity::getDeleted,CloseOrOpenEnum.CLOSE.getValue()).eq(FollowVpsEntity::getIsActive,CloseOrOpenEnum.OPEN.getValue()).eq(FollowVpsEntity::getIsMonitorRepair,CloseOrOpenEnum.OPEN.getValue()))).intValue());
        //漏单信号源
        AtomicReference<Integer> masterNum= new AtomicReference<>(0);
        //漏单跟单账号
        AtomicReference<Integer> slaveNum= new AtomicReference<>(0);
        List<RepairVpsVO> repairVpsVOList = new ArrayList<>(List.of());
        Map<Integer,Integer> vpsNumMap=new HashMap<>();
        List<FollowTraderEntity> slaves = followTraderService.list(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getType, TraderTypeEnum.SLAVE_REAL.getType()));
        Map<Long, FollowTraderEntity> slaveMap=new HashMap<>();
        if(ObjectUtil.isNotEmpty(slaves)){
            slaveMap= slaves.stream().collect(Collectors.toMap(FollowTraderEntity::getId, Function.identity()));
        }
        Map<String,Integer> followActiveMap=new HashMap<>();
        Map<Long, FollowTraderEntity> finalSlaveMap = slaveMap;
        followVpsEntityList.forEach(o->{
            RepairVpsVO repairVpsVO= RepairVpsVO.builder().build();
            repairVpsVO.setVpsName(o.getName());
            repairVpsVO.setConnectionStatus(o.getConnectionStatus());
            repairVpsVO.setIsActive(o.getIsActive());
            List<MasterRepairVO> masterRepairVOList=new ArrayList<>(List.of());
            List<FollowTraderEntity> list ;

            //除了admin都需要判断
         /*   if (!ObjectUtil.equals(Objects.requireNonNull(userId).toString(), "10000")) {
                //查看当前用户拥有的vps
                if (ObjectUtil.isNotEmpty(redisUtil.get(Constant.SYSTEM_VPS_USER + userId))) {
                    list = (List<VpsUserVO>) redisUtil.get(Constant.SYSTEM_VPS_USER + userId);
                } else {
                    List<FollowVpsUserEntity> vpsUserEntityList = followVpsUserService.list(new LambdaQueryWrapper<FollowVpsUserEntity>().eq(FollowVpsUserEntity::getUserId,userId));
                    if(ObjectUtil.isEmpty(vpsUserEntityList)){
                        return null;
                    }
                    List<VpsUserVO> vpsUserVOS = convertoVpsUser(vpsUserEntityList);
                    redisUtil.set(Constant.SYSTEM_VPS_USER + userId, JSONObject.toJSON(vpsUserVOS));
                    list = vpsUserVOS;
                }
            }*/
            if (masterAccount!=0){

                list=followTraderService.list(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getType, TraderTypeEnum.MASTER_REAL.getType()).like(FollowTraderEntity::getAccount,masterAccount).eq(FollowTraderEntity::getFollowStatus,CloseOrOpenEnum.OPEN.getValue()).eq(FollowTraderEntity::getIpAddr, o.getIpAddress()));
            }else{
                list=followTraderService.list(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getType, TraderTypeEnum.MASTER_REAL.getType()).eq(FollowTraderEntity::getFollowStatus,CloseOrOpenEnum.OPEN.getValue()).eq(FollowTraderEntity::getIpAddr, o.getIpAddress()));
            }

            if (slaveAccount!=0) {
                List<FollowTraderSubscribeEntity> subs = followTraderSubscribeService.list(new LambdaQueryWrapper<FollowTraderSubscribeEntity>().like(FollowTraderSubscribeEntity::getSlaveAccount, slaveAccount));
                if(ObjectUtil.isNotEmpty(subs)){
                    List<Long> masterIds = subs.stream().map(FollowTraderSubscribeEntity::getMasterId).collect(Collectors.toList());
                    list= followTraderService.list(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getIpAddr,o.getIpAddress()).in(FollowTraderEntity::getId,masterIds).eq(FollowTraderEntity::getFollowStatus,CloseOrOpenEnum.OPEN.getValue()));
                }else{
                    list=new ArrayList<>();
                }

            }
            //遍历账号信息
            //vps 漏单数量
            AtomicReference<Integer> num= new AtomicReference<>(0);


            list.forEach(trader->{
                MasterRepairVO masterRepairVO = MasterRepairVO.builder().build();
                masterRepairVO.setMasterAccount(Integer.valueOf(trader.getAccount()));
                masterRepairVO.setMasterPlatform(trader.getPlatform());
                masterRepairVO.setFollowStatus(trader.getFollowStatus());
                masterRepairVO.setStatus(trader.getStatus());
                //查询信号源下跟单的漏单信息
                List<OrderRepairInfoVO> orderRepairInfoVOList = Collections.synchronizedList(new ArrayList<>());/*
             Map<String, Map<Object, Object>> sendMap = redisUtil.getKeysByThreeConditions("follow:repair:send:*", o.getIpAddress(), trader.getPlatform(), trader.getAccount());
              Map<String, Map<Object, Object>> closeMap = redisUtil.getKeysByThreeConditions("follow:repair:close:*", o.getIpAddress(), trader.getPlatform(), trader.getAccount());
              Integer salvenum = getOrder(orderRepairInfoVOList, sendMap, closeMap, trader.getId(),o.getIpAddress());*/

              Map<Object, Object> objectObjectMap = redisCache.hGetStrAll(Constant.REPAIR_SEND+trader.getAccount()+":"+trader.getId());
              List<Object> objectList=new ArrayList<>();
                if(objectObjectMap!=null){
                    objectObjectMap.values().forEach(obj->{
                        JSONObject jsonObject = JSONObject.parseObject(obj.toString());
                        Collection<Object> values = jsonObject.values();
                        AtomicBoolean flag=new AtomicBoolean(false);
                        values.forEach(vs->{
                                OrderRepairInfoVO infoVO = JSONObject.parseObject(vs.toString(), OrderRepairInfoVO.class);
                             FollowTraderEntity followTraderEntity = finalSlaveMap.get(infoVO.getSlaveId());
                         if(followTraderEntity.getFollowStatus().equals(CloseOrOpenEnum.OPEN.getValue())) {
                             infoVO.setStatus(followTraderEntity.getStatus());
                             infoVO.setFollowStatus(followTraderEntity.getFollowStatus());
                             vpsNumMap.put(trader.getServerId(), 1);

                             if (slaveAccount != 0) {
                                 if (infoVO.getSlaveAccount().contains(slaveAccount.toString())) {
                                     orderRepairInfoVOList.add(infoVO);
                                     followActiveMap.put(infoVO.getSlaveAccount() + infoVO.getSlavePlatform(), 1);
                                 }
                             } else {
                                 orderRepairInfoVOList.add(infoVO);
                                 followActiveMap.put(infoVO.getSlaveAccount() + infoVO.getSlavePlatform(), 1);
                             }
                             flag.set(true);
                         }
                        });
                        if (flag.get()) {
                            objectList.add(obj);
                        }

                    });
                }

                Map<Object, Object> closeMap = redisCache.hGetStrAll(Constant.REPAIR_CLOSE+trader.getAccount()+":"+trader.getId());
                if(closeMap!=null){
                    closeMap.values().forEach(obj->{
                        JSONObject jsonObject = JSONObject.parseObject(obj.toString());
                        Collection<Object> values = jsonObject.values();
                        values.forEach(vs->{
                            OrderRepairInfoVO infoVO = JSONObject.parseObject(vs.toString(), OrderRepairInfoVO.class);
                            FollowTraderEntity followTraderEntity = finalSlaveMap.get(infoVO.getSlaveId());
                            if(followTraderEntity.getFollowStatus().equals(CloseOrOpenEnum.OPEN.getValue())) {
                                infoVO.setStatus(trader.getStatus());
                                infoVO.setFollowStatus(trader.getFollowStatus());
                                vpsNumMap.put(trader.getServerId(), 1);

                                if (slaveAccount != 0) {
                                    if (infoVO.getSlaveAccount().contains(slaveAccount.toString())) {
                                        orderRepairInfoVOList.add(infoVO);
                                        followActiveMap.put(infoVO.getSlaveAccount() + infoVO.getSlavePlatform(), 1);
                                    }
                                } else {
                                    orderRepairInfoVOList.add(infoVO);
                                    followActiveMap.put(infoVO.getSlaveAccount() + infoVO.getSlavePlatform(), 1);
                                }
                            }
                        });

                    });
                }
               // slaveNum.updateAndGet(v -> v + salvenum);
                slaveNum.updateAndGet(v -> v + objectList.size());
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
        repairDataVo.setFollowActiveNum(followActiveMap.keySet().size());
        repairDataVo.setVpsActiveNum(vpsNumMap.keySet().size());
        repairDataVo.setSourceActiveNum(masterNum.get());
        repairDataVo.setMasterNum(Long.valueOf(followTraderService.count(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getType,TraderTypeEnum.MASTER_REAL.getType()))).intValue());
        repairDataVo.setSlaveNum(Long.valueOf(followTraderService.count(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getType,TraderTypeEnum.SLAVE_REAL.getType()))).intValue());
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
            Set<Session> sessionSet = sessionPool.get(vpsId+masterAccount+slaveAccount+userId+"");
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
        if(sessionPool.get(vpsId+masterAccount+slaveAccount+userId+"")!=null){
            sessionPool.get(vpsId+masterAccount+slaveAccount+userId+"").remove(session);
        }
    }

}
