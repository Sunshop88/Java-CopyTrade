package net.maku.subcontrol.trader;

import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.subcontrol.service.IOperationStrategy;
import org.apache.kafka.clients.consumer.ConsumerRecord;


/**
 * MT跟单对象收到信号后，平仓操作
 *
 * @author samson bruce
 * @version V1.0
 * @since 2023/3/21
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
