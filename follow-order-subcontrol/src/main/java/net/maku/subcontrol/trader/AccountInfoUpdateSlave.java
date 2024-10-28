package net.maku.subcontrol.trader;

import net.maku.subcontrol.service.IOperationStrategy;
import org.apache.kafka.clients.consumer.ConsumerRecord;


/**
 * @author samson bruce
 */
public class AccountInfoUpdateSlave extends AbstractOperation implements IOperationStrategy {
    public AccountInfoUpdateSlave(CopierApiTrader copier4ApiTrader) {
        super(copier4ApiTrader.getTrader());
    }

    @Override
    public void operate(ConsumerRecord<String, Object> record, int retry) {

    }
}
