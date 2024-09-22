package net.maku.subcontrol.trader;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cld.message.pubsub.kafka.CldProducerRecord;
import net.maku.followcom.entity.FollowOrderActiveEntity;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.enums.AcEnum;
import net.maku.subcontrol.constants.KafkaTopicPrefixSuffix;
import net.maku.subcontrol.service.IOperationStrategy;
import net.maku.subcontrol.util.KafkaTopicUtil;
import org.apache.kafka.clients.consumer.ConsumerRecord;

/**
 * @author hipil
 */
public class OrderCheckMaster extends AbstractOperation implements IOperationStrategy {
    FollowTraderEntity leader;
    LeaderApiTrader leaderApiTrader;

    public OrderCheckMaster(LeaderApiTrader leaderApiTrader) {
        super(leaderApiTrader.getTrader());
        this.leaderApiTrader = leaderApiTrader;
        this.leader = this.leaderApiTrader.getTrader();
    }

    @Override
    public void operate(ConsumerRecord<String, Object> record, int retry) {

        FollowOrderActiveEntity copierActiveOrder = (FollowOrderActiveEntity) record.value();

        // 跟单者持仓订单，对应的喊单者的订单号
        long leaderTicket;
        try {
//            leaderTicket = Long.parseLong(copierActiveOrder.getComment().split("#")[2], 36);
//            //搜索出开仓change，主要是为了获取开仓时间
//            BchainOrderChange positionClose = this.orderChangeService.getOne(Wrappers.<BchainOrderChange>lambdaQuery()
//                    .eq(BchainOrderChange::getTicket, leaderTicket)
//                    .eq(BchainOrderChange::getTraderId, leader.getId())
//                    .eq(BchainOrderChange::getChangeType, OrderChangeTypeEnum.CLOSED));
//            if (positionClose != null) {
//                // 向跟单者发送平仓失败的订单
//                CldProducerRecord<String, Object> cldProducerRecord = new CldProducerRecord<>(KafkaTopicPrefixSuffix.TENANT, KafkaTopicUtil.copierAccountTopic(String.valueOf(copierActiveOrder.getTraderId())), AcEnum.FC.getKey(), copierActiveOrder);
//                leaderApiTrader.getKafkaProducer().send(cldProducerRecord, (recordMetadata, e) -> {
//                });
//            }
        } catch (Exception ignored) {
        }
    }
}
