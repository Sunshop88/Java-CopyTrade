package net.maku.subcontrol.trader.strategy;

import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.subcontrol.trader.LeaderApiTrader;
import org.apache.kafka.clients.consumer.ConsumerRecord;


/**
 * MT4 跟单者处理开仓信号策略
 *
 * @author samson bruce
 * @since 2023/04/14
 */
@Slf4j
public class OrderSendMaster extends AbstractOperation implements IOperationStrategy {
    FollowTraderEntity leader;
    LeaderApiTrader leaderApiTrader;

    public OrderSendMaster(LeaderApiTrader leaderApiTrader) {
        super(leaderApiTrader.getTrader());
        this.leaderApiTrader = leaderApiTrader;
        this.leader = this.leaderApiTrader.getTrader();
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
