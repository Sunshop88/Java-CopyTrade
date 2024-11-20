package net.maku.subcontrol.even;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cld.message.pubsub.kafka.IKafkaProducer;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowTraderSubscribeEntity;
import net.maku.subcontrol.trader.AbstractApiTrader;
import online.mtapi.mt4.OrderUpdateEventArgs;

import java.util.List;
/**
 * @author Samson Bruce
 */
@Slf4j
public class CopierOrderUpdateEventHandlerImpl extends OrderUpdateHandler {
    AbstractApiTrader copier4ApiTrader;

    public CopierOrderUpdateEventHandlerImpl(AbstractApiTrader abstract4ApiTrader, IKafkaProducer<String, Object> kafkaProducer) {
        super(kafkaProducer);
        this.copier4ApiTrader = abstract4ApiTrader;
    }

    @Override
    public void invoke(Object sender, OrderUpdateEventArgs orderUpdateEventArgs) {
        int flag=0;
        try {
            //发送websocket消息标识
            switch (orderUpdateEventArgs.Action) {
                case PositionOpen:
                case PendingFill:
                case PositionClose:
                    flag=1;
                    break;
                default:
                    log.error("Unexpected value: " + orderUpdateEventArgs.Action);
            }
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
        if (flag==1){
            //查看喊单信息
            List<FollowTraderSubscribeEntity> list = followTraderSubscribeService.list(new LambdaQueryWrapper<FollowTraderSubscribeEntity>().eq(FollowTraderSubscribeEntity::getSlaveId, copier4ApiTrader.getTrader().getId()));
            list.forEach(o->{
                //发送消息
                traderOrderActiveWebSocket.sendPeriodicMessage(o.getMasterId().toString(),leader.getId().toString());
            });
        }
    }
}
