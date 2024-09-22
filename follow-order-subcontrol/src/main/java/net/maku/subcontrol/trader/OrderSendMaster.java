package net.maku.subcontrol.trader;

import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.subcontrol.service.IOperationStrategy;
import org.apache.kafka.clients.consumer.ConsumerRecord;

@Slf4j
public class OrderSendMaster extends AbstractOperation implements IOperationStrategy {
    FollowTraderEntity leader;
    LeaderApiTrader copierApiTrader;

    public OrderSendMaster(LeaderApiTrader leaderApiTrader) {
        super(leaderApiTrader.getTrader());
        this.copierApiTrader = leaderApiTrader;
        this.leader = this.copierApiTrader.getTrader();
    }

    /**
     * 收到开仓信号处理操作
     *
     * @param record ConsumerRecord
     * @param retry  处理次数
     */
    @Override
    public void operate(ConsumerRecord<String, Object> record, int retry) {
        orderSend(record, retry, leader);
    }

}
