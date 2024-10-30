package net.maku.subcontrol.callable;

import com.cld.message.pubsub.kafka.KafkaMessageCallback;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.enums.AcEnum;
import net.maku.subcontrol.trader.*;
import net.maku.subcontrol.trader.strategy.OrderCloseMaster;
import net.maku.subcontrol.trader.strategy.OrderSendMaster;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;

/**
 * 喊单者监听KAFKA监听回调函数
 */
@Slf4j
public class LeaderKafkaMessageCallbackImpl extends AbstractKafkaMessageCallback implements KafkaMessageCallback<String, Object> {
    private final LeaderApiTrader LeaderApiTrader;

    public LeaderKafkaMessageCallbackImpl(LeaderApiTrader LeaderApiTrader) {
        this.LeaderApiTrader = LeaderApiTrader;
        operationStrategy.put(AcEnum.NEW, new OrderSendMaster(LeaderApiTrader));
        operationStrategy.put(AcEnum.CLOSED, new OrderCloseMaster(LeaderApiTrader));
        operationStrategy.put(AcEnum.SH, new AccountInfoUpdateMaster(LeaderApiTrader));
    }

    @Override
    public void onMessage(ConsumerRecords<String, Object> consumerRecords) {

    }

    @Override
    public void onMessage(ConsumerRecord<String, Object> consumerRecord) {
        log.info("喊单账号：{},收到{} {}", LeaderApiTrader.getTrader().getAccount(), consumerRecord.key(), consumerRecord.value());
        scheduledExecutorService.submit(() -> {
            try {
                tradeOperation(consumerRecord);
            } catch (Exception ex) {
                log.error("账号：{},收到{}-{}处理失败", LeaderApiTrader.getTrader().getAccount(), consumerRecord.key(), consumerRecord.value(), ex);
            }
        });
    }
}
