package net.maku.subcontrol.trader;

import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.subcontrol.rule.FollowRule;
import net.maku.subcontrol.service.IOperationStrategy;
import org.apache.kafka.clients.consumer.ConsumerRecord;


/**
 * MT 跟单者处理开仓信号策略
 *
 * @author samson bruce
 * @since 2023/0/1
 */
@Slf4j
public class OrderModifyMaster extends AbstractOperation implements IOperationStrategy {
    FollowTraderEntity leader;
    FollowRule followRule;
    LeaderApiTrader copierApiTrader;


    public OrderModifyMaster(LeaderApiTrader copierApiTrader) {
        super(copierApiTrader.getTrader());
        this.copierApiTrader = copierApiTrader;
        this.leader = this.copierApiTrader.getTrader();
        this.followRule = new FollowRule();
    }

    /**
     * 收到开仓信号处理操作
     *
     * @param record ConsumerRecord
     * @param retry  处理次数
     */
    @Override
    public void operate(ConsumerRecord<String, Object> record, int retry) {
        orderModify(record, retry, leader);
    }

}
