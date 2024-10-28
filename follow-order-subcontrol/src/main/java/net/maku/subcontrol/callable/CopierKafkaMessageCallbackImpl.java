package net.maku.subcontrol.callable;

import com.cld.message.pubsub.kafka.KafkaMessageCallback;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.enums.AcEnum;
import net.maku.subcontrol.trader.*;
import net.maku.subcontrol.trader.strategy.OrderCloseCopier;
import net.maku.subcontrol.trader.strategy.OrderSendCopier;
import online.mtapi.mt4.Exception.ConnectException;
import online.mtapi.mt4.Exception.TimeoutException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;


/**
 * 策略设计模式
 * KAFKA收到kafka消息的处理回调函数
 *
 * @author samson bruce
 * @since 2023/04/27
 */
@Slf4j
public class CopierKafkaMessageCallbackImpl extends AbstractKafkaMessageCallback implements KafkaMessageCallback<String, Object> {
    protected CopierApiTrader copierApiTrader;
    protected FollowTraderEntity copier;

    public CopierKafkaMessageCallbackImpl(CopierApiTrader copierApiTrader) {
        this.copierApiTrader = copierApiTrader;
        this.copier = this.copierApiTrader.getTrader();
        this.mapKey = copier.getId() + "#" + copier.getAccount();

        operationStrategy.put(AcEnum.NEW, new OrderSendCopier(copierApiTrader));
        operationStrategy.put(AcEnum.CLOSED, new OrderCloseCopier(copierApiTrader));
//        operationStrategy.put(AcEnum.FC, new OrderCloseSlave(copierApiTrader));
        operationStrategy.put(AcEnum.OTHERS, new AccountInfoUpdateSlave(copierApiTrader));
    }

    @Override
    public void onMessage(ConsumerRecords<String, Object> consumerRecords) {
        for (ConsumerRecord<String, Object> consumerRecord : consumerRecords) {
            log.debug("MT4跟单者：{}-{}-{}收到 {}---{}", copier.getId(), copier.getAccount(), copier.getServerName(), consumerRecord.key(), consumerRecord.value());
            scheduledExecutorService.submit(() -> {
                try {
                    tradeOperation(consumerRecord);
                } catch (ConnectException | TimeoutException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    @Override
    public void onMessage(ConsumerRecord<String, Object> consumerRecord) {

    }


}
