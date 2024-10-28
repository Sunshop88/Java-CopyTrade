package net.maku.subcontrol.trader;

import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.enums.AcEnum;
import net.maku.subcontrol.pojo.SynInfo;
import net.maku.subcontrol.service.IOperationStrategy;
import org.apache.kafka.clients.consumer.ConsumerRecord;

/**
 * @author hipil
 */
@Slf4j
public class AccountInfoUpdateMaster extends AbstractOperation implements IOperationStrategy {
    FollowTraderEntity leader;
    LeaderApiTrader leaderApiTrader;

    public AccountInfoUpdateMaster(LeaderApiTrader leaderApiTrader) {
        super(leaderApiTrader.getTrader());
        this.leaderApiTrader = leaderApiTrader;
        this.leader = leaderApiTrader.getTrader();
    }

    @Override
    public void operate(ConsumerRecord<String, Object> record, int retry) {
        switch (AcEnum.valueOf(record.key())) {
            case SH:
//                SynInfo synInfo = (SynInfo) record.value();
//                log.info("synInfo {}", synInfo);
//                threeStrategyThreadPoolExecutor.submit(new ApiAnalysisCallable(threeStrategyThreadPoolExecutor, leader, leaderApiTrader, synInfo));
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + AcEnum.valueOf(record.key()));
        }
    }
}
