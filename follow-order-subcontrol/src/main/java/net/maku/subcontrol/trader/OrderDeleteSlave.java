package net.maku.subcontrol.trader;

import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.subcontrol.service.IOperationStrategy;
import org.apache.kafka.clients.consumer.ConsumerRecord;

/**
 * MT跟单对象收到信号后，删除操作
 * 喊单者是不会向跟单者发送挂单信号，所有跟单者是不会出现删除的情况
 */
public class OrderDeleteSlave extends AbstractOperation implements IOperationStrategy {
    FollowTraderEntity copier;
    CopierApiTrader copierApiTrader;
    public OrderDeleteSlave(CopierApiTrader copierApiTrader) {
        super(copierApiTrader.getTrader());
        this.copierApiTrader = copierApiTrader;
        this.copier = this.copierApiTrader.getTrader();
    }

    @Override
    public void operate(ConsumerRecord<String, Object> record, int retry) {

    }
}
