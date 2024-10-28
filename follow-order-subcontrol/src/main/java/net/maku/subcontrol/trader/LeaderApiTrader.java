package net.maku.subcontrol.trader;

import com.cld.message.pubsub.kafka.IKafkaProducer;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.subcontrol.callable.LeaderKafkaMessageCallbackImpl;
import net.maku.subcontrol.constants.KafkaTopicPrefixSuffix;
import net.maku.subcontrol.task.UpdateTraderInfoTask;
import net.maku.subcontrol.util.KafkaTopicUtil;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * MetaTrader 4喊单者对象
 */
@Slf4j
public class LeaderApiTrader extends AbstractApiTrader {
    public LeaderApiTrader(FollowTraderEntity trader, IKafkaProducer<String, Object> kafkaProducer, String host, int port) throws IOException {
        super(trader, kafkaProducer, host, port);
    }

    public LeaderApiTrader(FollowTraderEntity trader, IKafkaProducer<String, Object> kafkaProducer, String host, int port, LocalDateTime closedOrdersFrom, LocalDateTime closedOrdersTo) throws IOException {
        super(trader, kafkaProducer, host, port, closedOrdersFrom, closedOrdersTo);
    }

    @Override
    public void connect2Broker() throws Exception {
        super.connect2Broker();
    }

    public Boolean startTrade(List<String> subscriptions) {
        return startTrade(SpringContextUtils.getBean(LeaderApiTradersAdmin.class).getKafkaConsumerCachedThreadPool(), subscriptions);
    }

    public Boolean startTrade() {
        //  订阅的喊单者主题
        List<String> subscriptions = Arrays.asList(KafkaTopicUtil.leaderAccountTopic(trader), KafkaTopicUtil.leaderTradeSignalTopic(trader));
        Boolean start = startTrade(SpringContextUtils.getBean(LeaderApiTradersAdmin.class).getKafkaConsumerCachedThreadPool(), subscriptions);
        if (!start) {
            log.error("喊单者账号:[{}-{}-{}],开始消费失败", trader.getId(), trader.getAccount(), trader.getServerName());
        } else {
            log.info("喊单者账号:[{}-{}-{}],开始消费成功", trader.getId(), trader.getAccount(), trader.getServerName());
        }
        return start;
    }

    private Boolean startTrade(ExecutorService executorService, List<String> subscriptions) {
        super.updateTradeInfoFuture = this.scheduledExecutorService.scheduleWithFixedDelay(new UpdateTraderInfoTask(this), 6, 30, TimeUnit.SECONDS);
        super.cldKafkaConsumer.setKafkaMessageCallback(new LeaderKafkaMessageCallbackImpl(this));
        return this.cldKafkaConsumer.startConsume(KafkaTopicPrefixSuffix.TENANT, subscriptions, executorService, new AotfxConsumerMasterOrderRebalanceListener(this.cldKafkaConsumer), 100);
    }

}
