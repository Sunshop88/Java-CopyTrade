package net.maku.subcontrol.trader.strategy;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.*;
import net.maku.followcom.enums.*;
import net.maku.followcom.pojo.EaOrderInfo;
import net.maku.followcom.service.*;
import net.maku.followcom.service.impl.*;
import net.maku.followcom.util.CommentGenerator;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.followcom.vo.OrderRepairInfoVO;
import net.maku.framework.common.cache.RedisUtil;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.utils.ThreadPoolUtils;
import net.maku.subcontrol.rule.FollowRule;
import net.maku.subcontrol.service.FollowOrderHistoryService;
import net.maku.subcontrol.service.FollowSubscribeOrderService;
import net.maku.subcontrol.service.impl.FollowOrderHistoryServiceImpl;
import net.maku.subcontrol.service.impl.FollowSubscribeOrderServiceImpl;
import net.maku.subcontrol.vo.FollowOrderRepairSocketVO;
import online.mtapi.mt4.Op;
import online.mtapi.mt4.Order;
import online.mtapi.mt4.QuoteClient;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.*;
import java.util.concurrent.*;

import static online.mtapi.mt4.Op.Buy;
import static online.mtapi.mt4.Op.Sell;


/**
 * @author samson bruce
 */
@Slf4j
public class AbstractOperation {
    protected FollowTraderSubscribeService followTraderSubscribeService;
    protected FollowSubscribeOrderService followSubscribeOrderService;
    protected RedisUtil redisUtil;
    protected FollowRule followRule;
    protected FollowOrderHistoryService followOrderHistoryService;
    protected FollowVarietyService followVarietyService;
    protected FollowTraderService followTraderService;
    protected FollowPlatformService followPlatformService;
    protected FollowVpsService followVpsService;
    protected FollowTraderLogService followTraderLogService;
    protected List<String> kafkaMessages ;
    protected List<String> kafkaCloseMessages ;
    private KafkaTemplate<Object, Object> kafkaTemplate;
    protected FollowOrderDetailService followOrderDetailService;
    protected FollowSysmbolSpecificationService followSysmbolSpecificationService;
    public AbstractOperation() {
        this.redisUtil = SpringContextUtils.getBean(RedisUtil.class);
        followTraderSubscribeService = SpringContextUtils.getBean(FollowTraderSubscribeServiceImpl.class);
        followSubscribeOrderService = SpringContextUtils.getBean(FollowSubscribeOrderServiceImpl.class);
        followRule = new FollowRule();
        this.followOrderHistoryService=SpringContextUtils.getBean(FollowOrderHistoryServiceImpl.class);
        this.followVarietyService=SpringContextUtils.getBean(FollowVarietyServiceImpl.class);
        this.followTraderService=SpringContextUtils.getBean(FollowTraderServiceImpl.class);
        this.followPlatformService=SpringContextUtils.getBean(FollowPlatformServiceImpl.class);
        this.followVpsService=SpringContextUtils.getBean(FollowVpsServiceImpl.class);
        this.followTraderLogService=SpringContextUtils.getBean(FollowTraderLogServiceImpl.class);
        this.kafkaCloseMessages= new CopyOnWriteArrayList<>();
        this.kafkaMessages= new CopyOnWriteArrayList<>();
        this.kafkaTemplate = SpringContextUtils.getBean(KafkaTemplate.class);
        this.followOrderDetailService=SpringContextUtils.getBean(FollowOrderDetailServiceImpl.class);
        this.followSysmbolSpecificationService=SpringContextUtils.getBean(FollowSysmbolSpecificationServiceImpl.class);
        startBatchSender();
    }

    protected String comment(FollowTraderSubscribeEntity followTraderSubscribeEntity,EaOrderInfo orderInfo,Integer serverId) {
        return CommentGenerator.generateComment(followTraderSubscribeEntity.getFixedComment(), ObjectUtil.isEmpty(followTraderSubscribeEntity.getCommentType())?99:followTraderSubscribeEntity.getCommentType(),followTraderSubscribeEntity.getDigits(),orderInfo,serverId);
    }

    protected Op op(EaOrderInfo orderInfo, FollowTraderSubscribeEntity leaderCopier) {
        Op op = Op.forValue(orderInfo.getType());
        if (DirectionEnum.REVERSE.getType().equals(leaderCopier.getFollowDirection())) {
            //反向跟单时不会跟随止损、止盈
            orderInfo.setSl(0.0);
            orderInfo.setTp(0.0);
            if (op == Buy) {
                op = Sell;
            } else if (op == Sell) {
                op = Buy;
            }
        } else {
            return op;
        }
        return op;
    }

    public void batchSendKafkaMessages() {
        try {
            List<String> messagesToSend = new ArrayList<>(kafkaMessages);
            kafkaMessages.clear();

            for (String message : messagesToSend) {
                log.info("kafka发送消息"+message);
                kafkaTemplate.send("order-send", message);
            }
            List<String> messagesToClose = new ArrayList<>(kafkaCloseMessages);
            kafkaCloseMessages.clear();

            for (String message : messagesToClose) {
                log.info("kafka发送Close消息"+message);
                kafkaTemplate.send("order-close", message);
            }
        } catch (Exception e) {
            log.error("批量发送 Kafka 消息异常", e);
        }
    }

    // 初始化定时任务
    public void startBatchSender() {
        CompletableFuture.runAsync(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    batchSendKafkaMessages();
                    TimeUnit.SECONDS.sleep(1); // 固定延迟
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    log.error("定时任务异常: ", e);
                }
            }
        }, ThreadPoolUtils.getExecutor()); // 使用虚拟线程
    }


    FollowOrderRepairSocketVO setRepairWebSocket(String traderId, String slaveId, QuoteClient quoteClient){
        Order[] orders = quoteClient.GetOpenedOrders();
        FollowTraderSubscribeEntity followTraderSubscribe = followTraderSubscribeService.subscription(Long.valueOf(slaveId), Long.valueOf(traderId));
        FollowTraderEntity master = followTraderService.getFollowById(Long.valueOf(traderId));
        FollowTraderEntity slave = followTraderService.getFollowById(Long.valueOf(slaveId));
        Map<Object,Object> sendRepair=redisUtil.hGetAll(Constant.FOLLOW_REPAIR_SEND + FollowConstant.LOCAL_HOST+"#"+slave.getPlatform()+"#"+master.getPlatform()+"#"+followTraderSubscribe.getSlaveAccount()+"#"+followTraderSubscribe.getMasterAccount());
        Map<Object,Object> closeRepair = redisUtil.hGetAll(Constant.FOLLOW_REPAIR_CLOSE + FollowConstant.LOCAL_HOST+"#"+slave.getPlatform()+"#"+master.getPlatform()+"#"+followTraderSubscribe.getSlaveAccount()+"#"+followTraderSubscribe.getMasterAccount());

        List<Object> sendRepairToExtract = new ArrayList<>();

        for (Object repairObj : sendRepair.keySet()) {
            EaOrderInfo repairComment = (EaOrderInfo) sendRepair.get(repairObj);
            boolean existsInActive = Arrays.asList(orders).stream().anyMatch(order -> repairComment.getTicket()==order.MagicNumber);
            if (!existsInActive) {
                sendRepairToExtract.add(repairComment);
            }
        }
        List<Object> closeRepairToRemove = new ArrayList<>();
        List<Object> closeRepairToExtract = new ArrayList<>();
        for (Object repairObj : closeRepair.keySet()) {
            EaOrderInfo repairComment = (EaOrderInfo) closeRepair.get(repairObj);
            boolean existsInActive = Arrays.asList(orders).stream().anyMatch(order -> repairComment.getTicket()==order.MagicNumber);
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
        FollowOrderRepairSocketVO followOrderRepairSocketVO=new FollowOrderRepairSocketVO();
        followOrderRepairSocketVO.setOrderRepairInfoVOList(list);
        return followOrderRepairSocketVO;
    }
}
