package net.maku.subcontrol.trader;

import com.cld.message.pubsub.kafka.IKafkaProducer;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.subcontrol.callable.CopierKafkaMessageCallbackImpl;
import net.maku.subcontrol.constants.KafkaTopicPrefixSuffix;
import net.maku.subcontrol.listener.impl.ConsumerRebalanceListenerImpl;
import net.maku.subcontrol.task.CycleCloseOrderTask;
import net.maku.subcontrol.task.UpdateTraderInfoTask;
import net.maku.subcontrol.util.KafkaTopicUtil;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * mtapiMT4跟单对象抽象类
 *
 * @author samson bruce
 */
@Slf4j
public class CopierApiTrader extends AbstractApiTrader {

    public CopierApiTrader(FollowTraderEntity trader, IKafkaProducer<String, Object> kafkaProducer, String host, int port) throws IOException {
        super(trader, kafkaProducer, host, port);
    }

    public CopierApiTrader(FollowTraderEntity trader, IKafkaProducer<String, Object> kafkaProducer, String host, int port, LocalDateTime closedOrdersFrom, LocalDateTime closedOrdersTo) throws IOException {
        super(trader, kafkaProducer, host, port, closedOrdersFrom, closedOrdersTo);
    }

    @Override
    public void connect2Broker() {
        try {
            super.connect2Broker();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Boolean startTrade(List<String> subscriptions) {
        return startTrade(SpringContextUtils.getBean(CopierApiTradersAdmin.class).getKafkaConsumerCachedThreadPool(), subscriptions);
    }

    public Boolean startTrade() {

        List<String> topics = new LinkedList<>();

        // 专门发送给跟单者消息的主题
        topics.add(KafkaTopicUtil.copierAccountTopic(trader));
        // 订阅的喊单者主题
        for (String subscription : followTraderSubscribeService.initSubscriptions(trader.getId())) {
            String topic = KafkaTopicUtil.leaderTradeSignalTopic(subscription);
            topics.add(topic);
        }
        Boolean start = startTrade(SpringContextUtils.getBean(CopierApiTradersAdmin.class).getKafkaConsumerCachedThreadPool(), topics);
        if (!start) {
            log.error("跟单者[{}-{}-{}],开始消费失败", trader.getId(), trader.getAccount(), trader.getServerName());
        } else {
            log.info("跟单者账号[{}-{}-{}],开始消费成功", trader.getId(), trader.getAccount(), trader.getServerName());
        }
        return start;
    }

    private Boolean startTrade(ExecutorService executorService, List<String> topics) {
        super.updateTradeInfoFuture = this.scheduledExecutorService.scheduleWithFixedDelay(new UpdateTraderInfoTask(CopierApiTrader.this), 2, 30, TimeUnit.SECONDS);
//        cycleCloseOrderTask = new CycleCloseOrderTask(CopierApiTrader.this, kafkaProducer);
//        super.cycleFuture = this.scheduledExecutorService.schedule(cycleCloseOrderTask, 60, TimeUnit.SECONDS);
        this.cldKafkaConsumer.setKafkaMessageCallback(new CopierKafkaMessageCallbackImpl(this));
        return this.cldKafkaConsumer.startConsume(KafkaTopicPrefixSuffix.TENANT, topics, executorService, new ConsumerRebalanceListenerImpl<>(this.cldKafkaConsumer), 100);
    }
}
