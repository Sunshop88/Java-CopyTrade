package net.maku.subcontrol.trader.strategy;

import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.*;
import net.maku.followcom.enums.*;
import net.maku.followcom.pojo.EaOrderInfo;
import net.maku.followcom.service.*;
import net.maku.followcom.service.impl.*;
import net.maku.followcom.util.CommentGenerator;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.framework.common.cache.RedisUtil;
import net.maku.framework.common.utils.ThreadPoolUtils;
import net.maku.subcontrol.rule.FollowRule;
import net.maku.subcontrol.service.FollowOrderHistoryService;
import net.maku.subcontrol.service.FollowSubscribeOrderService;
import net.maku.subcontrol.service.impl.FollowOrderHistoryServiceImpl;
import net.maku.subcontrol.service.impl.FollowSubscribeOrderServiceImpl;
import online.mtapi.mt4.Op;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.ArrayList;
import java.util.List;
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

    protected String comment(FollowTraderSubscribeEntity followTraderSubscribeEntity) {
        return CommentGenerator.generateComment(followTraderSubscribeEntity.getFixedComment(),followTraderSubscribeEntity.getCommentType(),followTraderSubscribeEntity.getDigits());
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
}
