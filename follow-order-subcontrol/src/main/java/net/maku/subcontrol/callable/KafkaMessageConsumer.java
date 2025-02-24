package net.maku.subcontrol.callable;

import cn.hutool.core.date.DateField;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.*;
import net.maku.followcom.enums.*;
import net.maku.followcom.pojo.EaOrderInfo;
import net.maku.followcom.service.*;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.vo.FollowTraderVO;
import net.maku.followcom.vo.OrderActiveInfoVO;
import net.maku.followcom.vo.OrderRepairInfoVO;
import net.maku.framework.common.cache.RedisUtil;
import net.maku.framework.common.cache.RedissonLockUtil;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.utils.ThreadPoolUtils;
import net.maku.framework.security.user.SecurityUser;
import net.maku.subcontrol.entity.FollowSubscribeOrderEntity;
import net.maku.subcontrol.service.FollowOrderHistoryService;
import net.maku.subcontrol.service.FollowSubscribeOrderService;
import net.maku.subcontrol.trader.*;
import online.mtapi.mt4.Op;
import online.mtapi.mt4.Order;
import online.mtapi.mt4.QuoteClient;
import org.springframework.cache.CacheManager;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
@Slf4j
@AllArgsConstructor
public class KafkaMessageConsumer {
    private final FollowSubscribeOrderService openOrderMappingService;
    private final FollowOrderDetailService followOrderDetailService;
    private final RedisUtil redisUtil;
    private final FollowVpsService followVpsService;
    private final FollowTraderLogService followTraderLogService;
    private final FollowSysmbolSpecificationService followSysmbolSpecificationService;
    private final FollowPlatformService followPlatformService;
    private final FollowOrderHistoryService followOrderHistoryService;
    private final CacheManager cacheManager;
    private final FollowTraderService followTraderService;
    private final LeaderApiTradersAdmin leaderApiTradersAdmin;
    private final CopierApiTradersAdmin copierApiTradersAdmin;
    private final FollowTraderSubscribeService followTraderSubscribeService;
    private final MessagesService messagesService;
    private final RedissonLockUtil redissonLockUtil;

    @KafkaListener(topics = "order-send", groupId = "order-group", containerFactory = "kafkaListenerContainerFactory")
    public void consumeMessageMasterSend(List<String> messages, Acknowledgment acknowledgment) {
        messages.forEach(message -> {
            ThreadPoolUtils.getExecutor().execute(()->{
                OrderResultEvent orderResultEvent = JSON.parseObject(message, OrderResultEvent.class);
                log.info("kafka消费" + orderResultEvent);
                if (ObjectUtil.isNotEmpty(orderResultEvent.getCopier().getIpAddr()) && orderResultEvent.getCopier().getIpAddr().equals(FollowConstant.LOCAL_HOST)) {
                    try {
                        handleOrderResult(
                                orderResultEvent.getOrder(),
                                orderResultEvent.getOrderInfo(),
                                orderResultEvent.getOpenOrderMapping(),
                                orderResultEvent.getCopier(),
                                orderResultEvent.getFlag(),
                                orderResultEvent.getStartTime(),
                                orderResultEvent.getEndTime(),
                                orderResultEvent.getStartPrice(),
                                orderResultEvent.getIpAddress()
                        );
                    } catch (Exception e) {
                        log.info("消费异常");
                    }
                }
                //漏单检查
                ThreadPoolUtils.getExecutor().execute(()-> {
                    //确保主账号持仓写入
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    FollowTraderEntity copier = orderResultEvent.getCopier();
                    FollowTraderEntity master = followTraderService.getFollowById(orderResultEvent.getOrderInfo().getMasterId());
                    repair(copier, master, null);
                });


            });
        });
        acknowledgment.acknowledge(); // 全部处理完成后提交偏移量
    }

    @KafkaListener(topics = "order-close", groupId = "order-group", containerFactory = "kafkaListenerContainerFactory")
    public void consumeMessageMasterClose(List<String> messages, Acknowledgment acknowledgment) {
        messages.forEach(message -> {
            ThreadPoolUtils.getExecutor().execute(()->{
                OrderResultCloseEvent orderResultEvent = JSON.parseObject(message, OrderResultCloseEvent.class);
                Order order = orderResultEvent.getOrder();
                FollowTraderEntity followTraderEntity = followTraderService.getFollowById(orderResultEvent.getCopier().getId());
                EaOrderInfo orderInfo = orderResultEvent.getOrderInfo();
                Integer flag=orderResultEvent.getFlag();
                log.info("kafka消费Close"+orderResultEvent);
                log.info("OrderClose平仓{}", order.Ticket);
                openOrderMappingService.update(Wrappers.<FollowSubscribeOrderEntity>lambdaUpdate()
                        .set(FollowSubscribeOrderEntity::getFlag, CopyTradeFlag.POS)
                        .set(FollowSubscribeOrderEntity::getMasterCloseTime, orderInfo.getCloseTime())
                        .set(FollowSubscribeOrderEntity::getMasterProfit, orderResultEvent.getLeaderProfit())
                        .set(FollowSubscribeOrderEntity::getDetectedCloseTime, orderInfo.getDetectedCloseTime())
                        .set(FollowSubscribeOrderEntity::getSlavePosition, order.Lots)
                        .set(FollowSubscribeOrderEntity::getSlaveProfit, orderResultEvent.getCopierProfit())
                        .set(FollowSubscribeOrderEntity::getSlaveReceiveCloseTime, orderInfo.getSlaveReceiveCloseTime())
                        .set(FollowSubscribeOrderEntity::getSlaveCloseTime, order.CloseTime)
                        .set(FollowSubscribeOrderEntity::getExtra, "平仓成功")
                        .set(FollowSubscribeOrderEntity::getSlaveComment, order.Comment)
                        .eq(FollowSubscribeOrderEntity::getMasterId, orderInfo.getMasterId())
                        .eq(FollowSubscribeOrderEntity::getSlaveId, followTraderEntity.getId())
                        .eq(FollowSubscribeOrderEntity::getMasterTicket, orderInfo.getTicket()));
                //生成日志
                FollowTraderLogEntity followTraderLogEntity = new FollowTraderLogEntity();
                followTraderLogEntity.setTraderType(TraderLogEnum.FOLLOW_OPERATION.getType());
                FollowVpsEntity followVpsEntity = followVpsService.getById(followTraderEntity.getServerId());
                followTraderLogEntity.setVpsId(followVpsEntity.getId());
                followTraderLogEntity.setVpsClient(followVpsEntity.getClientId());
                followTraderLogEntity.setVpsName(followVpsEntity.getName());
                followTraderLogEntity.setCreateTime(LocalDateTime.now());
                followTraderLogEntity.setType(flag == 0 ? TraderLogTypeEnum.CLOSE.getType() : TraderLogTypeEnum.REPAIR.getType());
                //跟单信息
                String remark = (flag == 0 ? FollowConstant.FOLLOW_CLOSE : FollowConstant.FOLLOW_REPAIR_CLOSE) + "策略账号=" + orderInfo.getAccount() + "单号=" + orderInfo.getTicket() +
                        "跟单账号=" + followTraderEntity.getAccount() + ",单号=" + order.Ticket + ",品种=" + order.Symbol + ",手数=" + order.Lots + ",类型=" + order.Type.name()+",节点="+orderResultEvent.getIpAddress();
                followTraderLogEntity.setLogDetail(remark);
                followTraderLogEntity.setCreator(ObjectUtil.isNotEmpty(SecurityUser.getUserId())?SecurityUser.getUserId():null);
                followTraderLogService.save(followTraderLogEntity);
                //详情
                FollowOrderDetailEntity detailServiceOne = followOrderDetailService.getOne(new LambdaQueryWrapper<FollowOrderDetailEntity>().eq(FollowOrderDetailEntity::getOrderNo, order.Ticket).eq(FollowOrderDetailEntity::getTraderId,followTraderEntity.getId()).eq(FollowOrderDetailEntity::getIpAddr, FollowConstant.LOCAL_HOST));
                if (ObjectUtil.isNotEmpty(detailServiceOne)) {
                    log.info("记录详情"+detailServiceOne.getTraderId()+"订单"+detailServiceOne.getOrderNo());
                    updateCloseOrder(detailServiceOne, order, orderResultEvent.getStartTime(), orderResultEvent.getEndTime(), orderResultEvent.getStartPrice(),orderResultEvent.getIpAddress());
                }
                //删除redis中的缓存
                String mapKey = followTraderEntity.getId() + "#" + followTraderEntity.getAccount();
                redisUtil.hDel(Constant.FOLLOW_SUB_ORDER + mapKey, Long.toString(orderInfo.getTicket()));
                //漏单检查
      /*          ThreadPoolUtils.getExecutor().execute(()-> {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    FollowTraderEntity master = followTraderService.getFollowById(orderInfo.getMasterId());
                    repair(followTraderEntity,master,null);
                });*/

            });
        });
        acknowledgment.acknowledge(); // 全部处理完成后提交偏移量
    }

    @KafkaListener(topics = "order-repair", groupId = "order-group", containerFactory = "kafkaListenerContainerFactory")
    public void consumeMessageMasterOrderRepair(List<String> messages, Acknowledgment acknowledgment) {
        messages.forEach(message -> {
            ThreadPoolUtils.getExecutor().execute(()->{
                log.info("kafka消费order-repair" + message);
                if (ObjectUtil.isNotEmpty(message)) {
                    try {
                        LeaderApiTrader leaderApiTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(message);
                        if (ObjectUtil.isNotEmpty(leaderApiTrader)){
                            Order[] orders = leaderApiTrader.quoteClient.GetOpenedOrders();
                            log.info("orders数量"+orders.length);
                            //查看跟单账号
                            List<FollowTraderSubscribeEntity> subscribeOrder = followTraderSubscribeService.getSubscribeOrder(Long.valueOf(message));
                            if (ObjectUtil.isNotEmpty(subscribeOrder)){
                                subscribeOrder.forEach(o->{
                                    ThreadPoolUtils.getExecutor().execute(()->{
                                        FollowTraderEntity slaveTrader = followTraderService.getFollowById(o.getSlaveId());
                                        //检查redis漏单数据（掉线回补漏单机制）
                                        FollowTraderEntity master =leaderApiTrader.getTrader();

                                        CopierApiTrader copierApiTrader = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(o.getSlaveId().toString());
                                        //漏单检查
                                    //    repair(slaveTrader,master,ObjectUtil.isNotEmpty(copierApiTrader)?copierApiTrader.quoteClient:null);
                                        Arrays.stream(orders).forEach(order->{
                                            ThreadPoolUtils.getExecutor().execute(()-> {
                                                //漏单记录为空 -添加漏单
                                                if (ObjectUtil.isEmpty(redisUtil.hGet(Constant.FOLLOW_REPAIR_SEND + FollowConstant.LOCAL_HOST + "#" + slaveTrader.getPlatform() + "#" + leaderApiTrader.getTrader().getPlatform() + "#" + o.getSlaveAccount() + "#" + leaderApiTrader.getTrader().getAccount(), String.valueOf(order.Ticket)))) {
                                                    EaOrderInfo eaOrderInfo = send2Copiers(OrderChangeTypeEnum.NEW, order, 0, leaderApiTrader.quoteClient.Account().currency, LocalDateTime.now(), leaderApiTrader.getTrader());
                                                    log.info(slaveTrader.getAccount()+"添加漏单"+order.Ticket);
                                                    redisUtil.hSet(Constant.FOLLOW_REPAIR_SEND + FollowConstant.LOCAL_HOST + "#" + slaveTrader.getPlatform() + "#" + leaderApiTrader.getTrader().getPlatform() + "#" + o.getSlaveAccount() + "#" + leaderApiTrader.getTrader().getAccount(), String.valueOf(order.Ticket), eaOrderInfo);
                                                    //发送漏单通知
                                                    messagesService.isRepairSend(eaOrderInfo, slaveTrader, master, ObjectUtil.isNotEmpty(copierApiTrader) ? copierApiTrader.quoteClient : null);
                                                }
                                            });
                                        });

                                        //漏平处理
                                        log.info(slaveTrader.getAccount()+"漏平处理");
                                        QuoteClient quoteClient;
                                        if (ObjectUtil.isEmpty(copierApiTrader)) {
                                            ConCodeEnum conCodeEnum = copierApiTradersAdmin.addTrader(slaveTrader);
                                            if (conCodeEnum == ConCodeEnum.SUCCESS) {
                                                log.info(slaveTrader.getAccount()+"启动成功+++");
                                                CopierApiTrader copierApiTrader1 = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(slaveTrader.getId().toString());
                                                copierApiTrader1.setTrader(slaveTrader);
                                                copierApiTrader1.startTrade();
                                                quoteClient = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(slaveTrader.getId().toString()).quoteClient;
                                            } else {
                                                quoteClient = null;
                                            }
                                        } else {
                                            quoteClient = copierApiTrader.quoteClient;
                                        }
                                        if (ObjectUtil.isEmpty(quoteClient))return;
                                        Order[] orders1 = quoteClient.GetOpenedOrders();
                                        String mapKey = slaveTrader.getId() + "#" + slaveTrader.getAccount();
                                        List<Integer> listOrder = Arrays.stream(orders).toList().stream().map(order -> order.Ticket).toList();
                                        List<Order> orderMaster = null;
                                        try {
                                            orderMaster = Arrays.asList(leaderApiTrader.quoteClient.DownloadOrderHistory(DateUtil.offset(DateUtil.date(), DateField.YEAR,-10).toLocalDateTime(), DateUtil.date().toLocalDateTime()));
                                        } catch (Exception e) {
                                            throw new RuntimeException(e);
                                        }
                                        List<Order> finalOrderMaster = orderMaster;
                                        Arrays.stream(orders1).toList().forEach(order1 -> {
                                            log.info(slaveTrader.getAccount()+"订单:"+order1.Ticket);
                                            //查看是否在订单关系列表里 并且主账号不包含该订单
                                            if (ObjectUtil.isNotEmpty(redisUtil.hGet(Constant.FOLLOW_SUB_ORDER + mapKey, Long.toString(order1.MagicNumber)))&& !listOrder.contains(order1.MagicNumber)){
                                                Optional<Order> first = finalOrderMaster.stream().filter(om -> om.Ticket == order1.MagicNumber).findFirst();
                                                if (first.isPresent()){
                                                    EaOrderInfo eaOrderInfo = send2Copiers(OrderChangeTypeEnum.CLOSED, first.get(), 0, leaderApiTrader.quoteClient.Account().currency, LocalDateTime.now(),leaderApiTrader.getTrader());
                                                    //删除漏单数据
                                                    //删除跟单redis记录
                                                    redisUtil.hDel(Constant.FOLLOW_REPAIR_SEND+ FollowConstant.LOCAL_HOST+"#"+slaveTrader.getPlatform()+"#"+leaderApiTrader.getTrader().getPlatform()+"#"+o.getSlaveAccount()+"#"+o.getMasterAccount(),String.valueOf(order1.MagicNumber));
                                                    log.info("插入kafka"+order1.MagicNumber);
                                                    //创建漏平数据
                                                    redisUtil.hSet(Constant.FOLLOW_REPAIR_CLOSE + FollowConstant.LOCAL_HOST+"#"+slaveTrader.getPlatform()+"#"+leaderApiTrader.getTrader().getPlatform()+"#"+o.getSlaveAccount()+"#"+o.getMasterAccount(),String.valueOf(order1.MagicNumber),eaOrderInfo);
                                                    //发送漏单通知
                                                    messagesService.isRepairClose(eaOrderInfo,slaveTrader,master);
                                                    //删除漏单redis记录
                                                    Object o1 = redisUtil.hGetStr(Constant.REPAIR_SEND + master.getAccount() + ":" + master.getId(), slaveTrader.getAccount().toString());
                                                    Map<Integer,OrderRepairInfoVO> repairInfoVOS = new HashMap();
                                                    if (o1!=null && o1.toString().trim().length()>0){
                                                        repairInfoVOS= JSONObject.parseObject(o1.toString(), Map.class);
                                                    }
                                                    repairInfoVOS.remove(eaOrderInfo.getTicket());
                                                    if(repairInfoVOS==null || repairInfoVOS.size()==0){
                                                        redisUtil.hDel(Constant.REPAIR_SEND + master.getAccount() + ":" + master.getId(),slaveTrader.getAccount().toString());
                                                    }else{
                                                        redisUtil.hSetStr(Constant.REPAIR_SEND + master.getAccount() + ":" + master.getId(), slaveTrader.getAccount().toString(),JSONObject.toJSONString(repairInfoVOS));
                                                    }
                                                    log.info("漏单删除,key:{},key:{},订单号:{},val:{},",Constant.REPAIR_SEND +master.getAccount() + ":" + master.getId(), slaveTrader.getAccount(),eaOrderInfo.getTicket(),JSONObject.toJSONString(repairInfoVOS) );
                                                }else {
                                                    log.info(slaveTrader.getAccount()+"暂无订单"+order1.Ticket);
                                                }
                                            }
                                        });
                                        log.info(slaveTrader.getAccount()+"多余漏单处理");
                                        //多余漏单删除
                                        Map<Object, Object> map = redisUtil.hGetAll(Constant.FOLLOW_REPAIR_SEND + FollowConstant.LOCAL_HOST + "#" + slaveTrader.getPlatform() + "#" + leaderApiTrader.getTrader().getPlatform() + "#" + o.getSlaveAccount() + "#" + o.getMasterAccount());
                                        map.keySet().stream().forEach(omap->{
                                            Optional<Order> first = finalOrderMaster.stream().filter(of -> omap.equals(of.Ticket)).findFirst();
                                            if (first.isPresent()){
                                                log.info("存在多余漏单"+omap);
                                                redisUtil.hDel(Constant.FOLLOW_REPAIR_SEND+ FollowConstant.LOCAL_HOST+"#"+slaveTrader.getPlatform()+"#"+leaderApiTrader.getTrader().getPlatform()+"#"+o.getSlaveAccount()+"#"+o.getMasterAccount(),String.valueOf(omap));
                                                //删除
                                                EaOrderInfo eaOrderInfo = send2Copiers(OrderChangeTypeEnum.CLOSED, first.get(), 0, leaderApiTrader.quoteClient.Account().currency, LocalDateTime.now(),leaderApiTrader.getTrader());
                                                Object o1 = redisUtil.hGetStr(Constant.REPAIR_SEND + master.getAccount() + ":" + master.getId(), slaveTrader.getAccount().toString());
                                                Map<Integer,OrderRepairInfoVO> repairInfoVOS = new HashMap();
                                                if (o1!=null && o1.toString().trim().length()>0){
                                                    repairInfoVOS= JSONObject.parseObject(o1.toString(), Map.class);
                                                }
                                                repairInfoVOS.remove(eaOrderInfo.getTicket());
                                                if(repairInfoVOS==null || repairInfoVOS.size()==0){
                                                    redisUtil.hDel(Constant.REPAIR_SEND + master.getAccount() + ":" + master.getId(),slaveTrader.getAccount().toString());
                                                }else{
                                                    redisUtil.hSetStr(Constant.REPAIR_SEND + master.getAccount() + ":" + master.getId(), slaveTrader.getAccount().toString(),JSONObject.toJSONString(repairInfoVOS));
                                                }
                                                log.info("漏单删除,key:{},key:{},订单号:{},val:{},",Constant.REPAIR_SEND +master.getAccount() + ":" + master.getId(), slaveTrader.getAccount(),eaOrderInfo.getTicket(),JSONObject.toJSONString(repairInfoVOS) );
                                            }
                                        });
                                    });
                                });
                            }
                        }else {
                            log.error("order-repair 该账户未登录"+message);
                        }
                    } catch (Exception e) {
                        log.error("消费异常");
                    }
                }
            });
        });
        acknowledgment.acknowledge(); // 全部处理完成后提交偏移量
    }
    @KafkaListener(topics = "order-repair-listener", groupId = "order-group", containerFactory = "kafkaListenerContainerFactory")
    public void consumeMessageMasterOrderRepairListener(List<String> messages, Acknowledgment acknowledgment) {
        messages.forEach(message -> {
            ThreadPoolUtils.getExecutor().execute(()->{
                log.info("kafka消费order-repair-listener" + message);
                if (ObjectUtil.isNotEmpty(message)) {
                    FollowTraderEntity trader = followTraderService.getFollowById(Long.valueOf(message));
                    if(trader.getType().equals(TraderTypeEnum.MASTER_REAL.getType())){
                            try {
                                LeaderApiTrader leaderApiTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(message);
                                if (ObjectUtil.isNotEmpty(leaderApiTrader)){
                                    //查看跟单账号
                                    List<FollowTraderSubscribeEntity> subscribeOrder = followTraderSubscribeService.getSubscribeOrder(Long.valueOf(message));
                                    if (ObjectUtil.isNotEmpty(subscribeOrder)){
                                        subscribeOrder.forEach(o->{
                                            ThreadPoolUtils.getExecutor().execute(()->{
                                                FollowTraderEntity slaveTrader = followTraderService.getFollowById(o.getSlaveId());
                                                //检查redis漏单数据（掉线回补漏单机制）
                                                FollowTraderEntity master =leaderApiTrader.getTrader();
                                                CopierApiTrader copierApiTrader = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(o.getSlaveId().toString());
                                                //漏单检查
                                                repair(slaveTrader,master,ObjectUtil.isNotEmpty(copierApiTrader)?copierApiTrader.quoteClient:null);

                                            });
                                        });
                                    }
                                }else {
                                    log.error("order-repair-listener 该账户未登录"+message);
                                }
                            } catch (Exception e) {
                                log.error("消费异常");
                            }
                       //跟单账号重连
                    }else {
                        CopierApiTrader copierApiTrader = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(Long.valueOf(message));
                        FollowTraderEntity slaveTrader = followTraderService.getFollowById(Long.valueOf(message));
                        FollowTraderSubscribeEntity subscribeEntity = followTraderSubscribeService.getOne(new LambdaQueryWrapper<FollowTraderSubscribeEntity>().eq(FollowTraderSubscribeEntity::getSlaveId, slaveTrader.getId()));
                        FollowTraderEntity master = followTraderService.getFollowById(subscribeEntity.getMasterId());
                        repair(slaveTrader,master,ObjectUtil.isNotEmpty(copierApiTrader)?copierApiTrader.quoteClient:null);

                    }
                }
            });
        });

        acknowledgment.acknowledge(); // 全部处理完成后提交偏移量
    }
    /**
     * 漏单回补机制
     * **/
    public void repair(FollowTraderEntity follow, FollowTraderEntity master, QuoteClient quoteClient){
        //漏开检查
        //检查漏开记录
        String openKey = Constant.REPAIR_SEND + "：" + follow.getAccount();
        boolean lock = redissonLockUtil.lock(openKey, 30, -1, TimeUnit.SECONDS);
        try {
            if(lock) {
                //如果主账号这边都平掉了,就删掉这笔订单
                Object o1 = redisUtil.get(Constant.TRADER_ACTIVE + master.getId());
                List<OrderActiveInfoVO> orderActiveInfoList = new ArrayList<>();
                if (ObjectUtil.isNotEmpty(o1)) {
                    orderActiveInfoList = JSONObject.parseArray(o1.toString(), OrderActiveInfoVO.class);
                }
                Map<Integer, OrderRepairInfoVO> repairInfoVOS = new HashMap<Integer, OrderRepairInfoVO>();
                if(orderActiveInfoList!=null && orderActiveInfoList.size()>0) {
                    orderActiveInfoList.stream().forEach(orderInfo -> {
                        AtomicBoolean existsInActive = new AtomicBoolean(true);
                        if (quoteClient != null) {
                            existsInActive.set(Arrays.stream(quoteClient.GetOpenedOrders()).anyMatch(order -> String.valueOf(orderInfo.getOrderNo()).equalsIgnoreCase(order.MagicNumber + "")));
                        } else {
                            Object o2 = redisUtil.get(Constant.TRADER_ACTIVE + follow.getId());
                            List<OrderActiveInfoVO> followActiveInfoList = new ArrayList<>();
                            if (ObjectUtil.isNotEmpty(o2)) {
                                followActiveInfoList = JSONObject.parseArray(o2.toString(), OrderActiveInfoVO.class);
                            }
                            existsInActive.set(followActiveInfoList.stream().anyMatch(order -> String.valueOf(orderInfo.getOrderNo()).equalsIgnoreCase(order.getMagicNumber().toString())));
                        }
                        if (!existsInActive.get()) {
                            OrderRepairInfoVO orderRepairInfoVO = new OrderRepairInfoVO();
                            orderRepairInfoVO.setRepairType(TraderRepairOrderEnum.SEND.getType());
                            orderRepairInfoVO.setMasterLots(orderInfo.getLots());
                            orderRepairInfoVO.setMasterOpenTime(orderInfo.getOpenTime());
                            orderRepairInfoVO.setMasterProfit(orderInfo.getProfit());
                            orderRepairInfoVO.setMasterSymbol(orderInfo.getSymbol());
                            orderRepairInfoVO.setMasterTicket(orderInfo.getOrderNo());
                            orderRepairInfoVO.setMasterOpenPrice(orderInfo.getOpenPrice());
                            orderRepairInfoVO.setMasterType(orderInfo.getType());
                            orderRepairInfoVO.setMasterId(master.getId());
                            orderRepairInfoVO.setSlaveAccount(follow.getAccount());
                            orderRepairInfoVO.setSlaveType(orderInfo.getType());
                            orderRepairInfoVO.setSlavePlatform(follow.getPlatform());
                            orderRepairInfoVO.setSlaveId(follow.getId());
                            repairInfoVOS.put(orderInfo.getOrderNo(), orderRepairInfoVO);
                        }
                        redisUtil.hSetStr(Constant.REPAIR_SEND + master.getAccount() + ":" + master.getId(), follow.getAccount().toString(), JSON.toJSONString(repairInfoVOS));
                        log.info("漏开补偿数据写入,key:{},key:{},订单号:{},val:{},", Constant.REPAIR_SEND + master.getAccount() + ":" + master.getId(), follow.getAccount().toString(), orderInfo.getOrderNo(), JSONObject.toJSONString(repairInfoVOS));
                    });
                }else{
                    redisUtil.hDel(Constant.REPAIR_SEND + master.getAccount() + ":" + master.getId(), follow.getAccount().toString());
                }
            }
            } catch (Exception e) {
                log.error("漏单检查写入异常"+e);
            }finally {
                redissonLockUtil.unlock(openKey);
            }


        //检查漏平记录
        String closekey = Constant.REPAIR_CLOSE + "：" + follow.getAccount();
        boolean closelock = redissonLockUtil.lock(closekey, 10, -1, TimeUnit.SECONDS);
        try {
            if(closelock) {
                Map<Integer, OrderRepairInfoVO> repairCloseNewVOS = new HashMap();
                Object o1 = redisUtil.hGetStr(Constant.REPAIR_CLOSE + master.getAccount() + ":" + master.getId(), follow.getAccount().toString());
                Map<Integer, JSONObject> repairVos = new HashMap();
                if (o1!=null && o1.toString().trim().length()>0){
                    repairVos= JSONObject.parseObject(o1.toString(), Map.class);
                }

                Object o2 = redisUtil.get(Constant.TRADER_ACTIVE + follow.getId());
                List<OrderActiveInfoVO> followActiveInfoList = new ArrayList<>();
                if (ObjectUtil.isNotEmpty(o2)) {
                    followActiveInfoList = JSONObject.parseArray(o2.toString(), OrderActiveInfoVO.class);
                }
                List<OrderActiveInfoVO> finalFollowActiveInfoList = followActiveInfoList;
                repairVos.forEach((k, v)->{
                    if(quoteClient!=null){
                        List<Order> list = Arrays.stream(quoteClient.GetOpenedOrders()).toList();
                        if(list.size()>1) {
                            Boolean flag = list.stream().anyMatch(order -> String.valueOf(k).equalsIgnoreCase(order.MagicNumber + ""));
                            if (flag) {
                                List<FollowOrderDetailEntity> detailServiceList = followOrderDetailService.list(new LambdaQueryWrapper<FollowOrderDetailEntity>().eq(FollowOrderDetailEntity::getTraderId, follow.getId()).eq(FollowOrderDetailEntity::getMagical, k));
                                if (ObjectUtil.isNotEmpty(detailServiceList) && detailServiceList.get(0).getCloseStatus().equals(CloseOrOpenEnum.CLOSE.getValue()) ) {
                                    OrderRepairInfoVO infoVO = JSONObject.parseObject(v.toJSONString(), OrderRepairInfoVO.class);
                                    repairCloseNewVOS.put(k,infoVO);
                                }
                            }
                        }
                    }else{
                        if(finalFollowActiveInfoList.size()>1) {
                            Boolean flag = finalFollowActiveInfoList.stream().anyMatch(order -> String.valueOf(k).equalsIgnoreCase(order.getMagicNumber().toString()));
                            if (flag) {
                                List<FollowOrderDetailEntity> detailServiceList = followOrderDetailService.list(new LambdaQueryWrapper<FollowOrderDetailEntity>().eq(FollowOrderDetailEntity::getTraderId, follow.getId()).eq(FollowOrderDetailEntity::getMagical, k));
                                if (ObjectUtil.isNotEmpty(detailServiceList) && detailServiceList.get(0).getCloseStatus().equals(CloseOrOpenEnum.CLOSE.getValue()) ) {
                                    OrderRepairInfoVO infoVO = JSONObject.parseObject(v.toJSONString(), OrderRepairInfoVO.class);
                                    repairCloseNewVOS.put(k,infoVO);
                                }
                            }
                        }
                    }


                });
                if(repairCloseNewVOS==null || repairCloseNewVOS.size()==0){
                    redisUtil.hDel(Constant.REPAIR_CLOSE + master.getAccount() + ":" + master.getId(), follow.getAccount().toString());
                }else{
                    redisUtil.hSetStr(Constant.REPAIR_CLOSE + master.getAccount() + ":" + master.getId(), follow.getAccount().toString(),JSONObject.toJSONString(repairCloseNewVOS));
                }
                log.info("漏平补偿检查写入数据,跟单账号:{},数据：{}",follow.getAccount(),JSONObject.toJSONString(repairCloseNewVOS));
          }
        } catch (Exception e) {
            log.error("漏平检查写入异常"+e);
        }finally {
            redissonLockUtil.unlock(closekey);
        }


    }
    protected EaOrderInfo send2Copiers(OrderChangeTypeEnum type, Order order, double equity, String currency, LocalDateTime detectedDate,FollowTraderEntity leader) {

        // 并且要给EaOrderInfo添加额外的信息：喊单者id+喊单者账号+喊单者服务器
        // #84 喊单者发送订单前需要处理前后缀
        EaOrderInfo orderInfo = new EaOrderInfo(order, leader.getId() ,leader.getAccount(), leader.getServerName(), equity, currency, Boolean.FALSE);
        assembleOrderInfo(type, orderInfo, detectedDate);
        return orderInfo;
    }

    void assembleOrderInfo(OrderChangeTypeEnum type, EaOrderInfo orderInfo, LocalDateTime detectedDate) {
        if (type == OrderChangeTypeEnum.NEW) {
            orderInfo.setOriginal(AcEnum.MO);
            orderInfo.setDetectedOpenTime(detectedDate);
        } else if (type == OrderChangeTypeEnum.CLOSED) {
            orderInfo.setDetectedCloseTime(detectedDate);
            orderInfo.setOriginal(AcEnum.MC);
        } else if (type == OrderChangeTypeEnum.MODIFIED) {
            orderInfo.setOriginal(AcEnum.MM);
        }
    }

    private void updateCloseOrder(FollowOrderDetailEntity followOrderDetailEntity, Order order, LocalDateTime startTime, LocalDateTime endTime, double price,String ipaddr) {
        //保存平仓信息
        followOrderDetailEntity.setCloseTimeDifference((int)order.sendTimeDifference);
        followOrderDetailEntity.setRequestCloseTime(startTime);
        followOrderDetailEntity.setResponseCloseTime(endTime);
        followOrderDetailEntity.setCloseTime(order.CloseTime);
        followOrderDetailEntity.setClosePrice(BigDecimal.valueOf(order.ClosePrice));
        followOrderDetailEntity.setRequestClosePrice(new BigDecimal(price));
        followOrderDetailEntity.setSwap(BigDecimal.valueOf(order.Swap));
        followOrderDetailEntity.setCommission(BigDecimal.valueOf(order.Commission));
        followOrderDetailEntity.setProfit(BigDecimal.valueOf(order.Profit));
        followOrderDetailEntity.setCloseStatus(CloseOrOpenEnum.OPEN.getValue());
        FollowVpsEntity vps = followVpsService.getVps(FollowConstant.LOCAL_HOST);
        followOrderDetailEntity.setCloseServerName(vps.getName());
        followOrderDetailEntity.setCloseServerHost(ipaddr);
        followOrderDetailEntity.setCloseIpAddr(FollowConstant.LOCAL_HOST);
        followOrderDetailEntity.setCloseId(0);
        followOrderDetailEntity.setRemark(null);
        //获取symbol信息
        Map<String, FollowSysmbolSpecificationEntity> specificationEntityMap = followSysmbolSpecificationService.getByTraderId(followOrderDetailEntity.getTraderId());
        FollowSysmbolSpecificationEntity followSysmbolSpecificationEntity = specificationEntityMap.get(followOrderDetailEntity.getSymbol());
        BigDecimal hd;
        if (followSysmbolSpecificationEntity.getProfitMode().equals("Forex")) {
            //如果forex 并包含JPY 也是100
            if (followOrderDetailEntity.getSymbol().contains("JPY")) {
                hd = new BigDecimal("100");
            } else {
                hd = new BigDecimal("10000");
            }
        } else {
            //如果非forex 都是 100
            hd = new BigDecimal("100");
        }
        followOrderDetailEntity.setClosePriceSlip(followOrderDetailEntity.getClosePrice().subtract(followOrderDetailEntity.getRequestClosePrice()).multiply(hd).abs());
        followOrderDetailService.updateById(followOrderDetailEntity);
    }

    private void handleOrderResult(Order order, EaOrderInfo orderInfo,
                                   FollowSubscribeOrderEntity openOrderMapping, FollowTraderEntity copier, Integer flag, LocalDateTime startTime, LocalDateTime endTime,double price,String ip) {
        copier=followTraderService.getFollowById(copier.getId());
        // 处理下单成功结果，记录日志和缓存
        log.info("[MT4跟单者:{}] 下单成功, 订单: {}", copier.getAccount(), order);

        // 数据持久化操作
        persistOrderMapping(openOrderMapping, order, orderInfo, copier, startTime, endTime,price,ip);

        // 日志记录
        logFollowOrder(copier, orderInfo, openOrderMapping, flag,ip);

    }


//    private void handleOrderFailure(EaOrderInfo orderInfo, FollowSubscribeOrderEntity openOrderMapping, Exception e) {
//        openOrderMapping.setSlaveType(Op.forValue(orderInfo.getType()).getValue());
//        openOrderMapping.setSlaveTicket(null);
//        openOrderMapping.setFlag(CopyTradeFlag.OF1);
//        openOrderMapping.setExtra("[开仓]即时价格开仓失败" + e.getMessage());
//
//        // 数据持久化失败状态
//        persistOrderMapping(openOrderMapping);
//    }

    private void persistOrderMapping(FollowSubscribeOrderEntity openOrderMapping, Order order, EaOrderInfo orderInfo, FollowTraderEntity trader, LocalDateTime startTime, LocalDateTime endTime,double price,String ip) {
        FollowPlatformEntity platForm = followPlatformService.getPlatFormById(trader.getPlatformId().toString());
        log.info("记录详情"+trader.getId()+"订单"+order.Ticket);
        FollowOrderDetailEntity followOrderDetailEntity = new FollowOrderDetailEntity();
        followOrderDetailEntity.setOpenTimeDifference((int)order.sendTimeDifference);
        followOrderDetailEntity.setRequestOpenPrice(BigDecimal.valueOf(price));
        followOrderDetailEntity.setTraderId(trader.getId());
        followOrderDetailEntity.setAccount(trader.getAccount());
        followOrderDetailEntity.setSymbol(orderInfo.getSymbol());
        followOrderDetailEntity.setCreator(SecurityUser.getUserId());
        followOrderDetailEntity.setCreateTime(LocalDateTime.now());
        followOrderDetailEntity.setSendNo("11111");
        followOrderDetailEntity.setType(orderInfo.getType());
        followOrderDetailEntity.setPlacedType(orderInfo.getPlaceType());
        followOrderDetailEntity.setPlatform(trader.getPlatform());
        followOrderDetailEntity.setBrokeName(platForm.getBrokerName());
        followOrderDetailEntity.setIpAddr(trader.getIpAddr());
        followOrderDetailEntity.setServerName(trader.getServerName());
        followOrderDetailEntity.setCommission(BigDecimal.valueOf(order.Commission));
        followOrderDetailEntity.setOpenTime(DateUtil.toLocalDateTime(DateUtil.offsetHour(DateUtil.date(order.OpenTime), -8)));
        followOrderDetailEntity.setOpenPrice(BigDecimal.valueOf(order.OpenPrice));
        followOrderDetailEntity.setOrderNo(order.Ticket);
        followOrderDetailEntity.setRequestOpenTime(startTime);
        followOrderDetailEntity.setResponseOpenTime(endTime);
        followOrderDetailEntity.setSize(BigDecimal.valueOf(order.Lots));
        followOrderDetailEntity.setSl(BigDecimal.valueOf(order.StopLoss));
        followOrderDetailEntity.setSwap(BigDecimal.valueOf(order.Swap));
        followOrderDetailEntity.setTp(BigDecimal.valueOf(order.TakeProfit));
        followOrderDetailEntity.setRateMargin(order.RateMargin);
        followOrderDetailEntity.setMagical(orderInfo.getTicket());
        followOrderDetailEntity.setSourceUser(orderInfo.getAccount());
        followOrderDetailEntity.setServerHost(ip);
        followOrderDetailService.save(followOrderDetailEntity);
        //滑点分析
        updateSendOrder(trader.getId(),order.Ticket);
    }


    private void logFollowOrder(FollowTraderEntity copier, EaOrderInfo orderInfo, FollowSubscribeOrderEntity openOrderMapping, Integer flag,String ip) {
        FollowTraderLogEntity logEntity = new FollowTraderLogEntity();
        FollowVpsEntity followVpsEntity = followVpsService.getById(copier.getServerId());
        logEntity.setVpsClient(followVpsEntity.getClientId());
        logEntity.setVpsId(copier.getServerId());
        logEntity.setVpsName(copier.getServerName());
        logEntity.setTraderType(TraderLogEnum.FOLLOW_OPERATION.getType());
        logEntity.setCreateTime(LocalDateTime.now());
        logEntity.setType(flag == 0 ? TraderLogTypeEnum.SEND.getType() : TraderLogTypeEnum.REPAIR.getType());
        String remark = (flag == 0 ? FollowConstant.FOLLOW_SEND : FollowConstant.FOLLOW_REPAIR_SEND)
                + ", 策略账号=" + orderInfo.getAccount()
                + ", 单号=" + orderInfo.getTicket()
                + ", 跟单账号=" + openOrderMapping.getSlaveAccount()
                + ", 品种=" + openOrderMapping.getSlaveSymbol()
                + ", 手数=" + openOrderMapping.getSlaveLots()
                + ", 类型=" + Op.forValue(openOrderMapping.getSlaveType()).name()
                + ", 节点=" + ip
                ;
        logEntity.setLogDetail(remark);
        logEntity.setCreator(ObjectUtil.isNotEmpty(SecurityUser.getUserId())?SecurityUser.getUserId():null);
        followTraderLogService.save(logEntity);
    }


    private void updateSendOrder(long traderId, Integer orderNo) {
        //获取symbol信息
        Map<String, FollowSysmbolSpecificationEntity> specificationEntityMap = followSysmbolSpecificationService.getByTraderId(traderId);
        //查看下单所有数据
        List<FollowOrderDetailEntity> list = followOrderDetailService.list(new LambdaQueryWrapper<FollowOrderDetailEntity>().eq(FollowOrderDetailEntity::getOrderNo, orderNo));
        //进行滑点分析
        list.stream().filter(o -> ObjectUtil.isNotEmpty(o.getOpenTime())).collect(Collectors.toList()).forEach(o -> {
            ThreadPoolUtils.getExecutor().execute(()->{
                FollowSysmbolSpecificationEntity followSysmbolSpecificationEntity = specificationEntityMap.get(o.getSymbol());
                BigDecimal hd;
                //增加一下判空
                if (ObjectUtil.isNotEmpty(followSysmbolSpecificationEntity) && followSysmbolSpecificationEntity.getProfitMode().equals("Forex")) {
                    //如果forex 并包含JPY 也是100
                    if (o.getSymbol().contains("JPY")) {
                        hd = new BigDecimal("100");
                    } else {
                        hd = new BigDecimal("10000");
                    }
                } else {
                    //如果非forex 都是 100
                    hd = new BigDecimal("100");
                }
                o.setOpenPriceSlip(o.getOpenPrice().subtract(o.getRequestOpenPrice()).multiply(hd).abs());
                followOrderDetailService.updateById(o);
            });
        });
    }
}