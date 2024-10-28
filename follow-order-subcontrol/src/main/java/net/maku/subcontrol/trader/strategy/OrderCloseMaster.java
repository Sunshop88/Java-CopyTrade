package net.maku.subcontrol.trader.strategy;

import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.subcontrol.service.IOperationStrategy;
import net.maku.subcontrol.trader.AbstractOperation;
import net.maku.subcontrol.trader.LeaderApiTrader;
import org.apache.kafka.clients.consumer.ConsumerRecord;


/**
 * MT跟单对象收到信号后，平仓操作
 */
@Slf4j
public class OrderCloseMaster extends AbstractOperation implements IOperationStrategy {
    FollowTraderEntity leader;
    LeaderApiTrader leaderApiTrader;

    public OrderCloseMaster(LeaderApiTrader leaderApiTrader) {
        super(leaderApiTrader.getTrader());
        this.leaderApiTrader = leaderApiTrader;
        this.leader = this.leaderApiTrader.getTrader();
    }

    @Override
    public void operate(ConsumerRecord<String, Object> record, int retry) {
        orderClose(record, retry, leader);
    }


}
