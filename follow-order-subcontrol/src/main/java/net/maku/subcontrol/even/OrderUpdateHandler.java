package net.maku.subcontrol.even;

import com.cld.message.pubsub.kafka.CldProducerRecord;
import com.cld.message.pubsub.kafka.IKafkaProducer;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.enums.AcEnum;
import net.maku.followcom.enums.OrderChangeTypeEnum;
import net.maku.followcom.pojo.EaOrderInfo;
import net.maku.framework.common.utils.ThreadPoolUtils;
import net.maku.subcontrol.constants.KafkaTopicPrefixSuffix;
import net.maku.followcom.service.FollowSubscribeOrderService;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.subcontrol.trader.AbstractApiTrader;
import online.mtapi.mt4.OrderUpdateEventArgs;
import online.mtapi.mt4.OrderUpdateEventHandler;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * @author samson bruce
 */
@Slf4j
@Data
public class OrderUpdateHandler implements OrderUpdateEventHandler {

    protected String topic;
    protected FollowTraderEntity leader;
    protected AbstractApiTrader abstractApiTrader;
    protected ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;
    protected IKafkaProducer<String, Object> kafkaProducer;
    protected ScheduledThreadPoolExecutor analysisExecutorService;

    protected FollowSubscribeOrderService followSubscribeOrderService;

    protected Boolean running = Boolean.TRUE;

    public OrderUpdateHandler(IKafkaProducer<String, Object> kafkaProducer) {
        this.kafkaProducer = kafkaProducer;
        this.scheduledThreadPoolExecutor = SpringContextUtils.getBean("scheduledExecutorService", ScheduledThreadPoolExecutor.class);
        this.analysisExecutorService = ThreadPoolUtils.getScheduledExecute();
        this.followSubscribeOrderService = SpringContextUtils.getBean(FollowSubscribeOrderService.class);
    }

    /**
     * 向消费主题，发送交易信号，供MT4MT5跟单者消费。
     *
     * @param type         交易类型
     * @param order        订单信息
     * @param equity       喊单者的净值
     * @param currency     喊单者的存款货币
     * @param detectedDate 侦测到交易动作的时间
     */
    protected void send2Copiers(OrderChangeTypeEnum type, online.mtapi.mt4.Order order, double equity, String currency, LocalDateTime detectedDate) {

        // KAFKA传输的信息需要序列化，所以需要将OrderInfo构建为EaOrderInfo,
        // 并且要给EaOrderInfo添加额外的信息：喊单者id+喊单者账号+喊单者服务器
        // #84 喊单者发送订单前需要处理前后缀
        EaOrderInfo orderInfo = new EaOrderInfo(order, leader.getId() ,leader.getAccount(), leader.getServerName(), equity, currency, Boolean.FALSE);
        assembleOrderInfo(type, orderInfo, detectedDate);
        CldProducerRecord<String, Object> cldProducerRecord = new CldProducerRecord<>(KafkaTopicPrefixSuffix.TENANT, topic, type.getValue(), orderInfo);
        kafkaProducer.send(cldProducerRecord);
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


    /**
     * 后续可以屏蔽该函数
     *
     * @param openTime  订单开仓时间
     * @param closeTime 订单平仓时间
     * @return milliseconds
     */
    protected int delaySendCloseSignal(LocalDateTime openTime, LocalDateTime closeTime) {
        // 开仓后立刻平仓，容易出现一个现象就是跟单者新开订单还没开出来，平仓信号到了后，会出现平仓失败。
        // 虽然最后循环平仓也会成功，但是会做一个优化就是开平仓时间间隔很短的平仓信号都做一个延迟。实际的交易很少会出现这种情况，只是做一个优化。
        try {
            Duration duration = Duration.between(openTime, closeTime).abs();
            int milliseconds = 2000;
            if (duration.get(ChronoUnit.MILLIS) < milliseconds) {
                return 1000;
            } else {
                return 0;
            }
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public void invoke(Object o, OrderUpdateEventArgs orderUpdateEventArgs) {

    }
}
