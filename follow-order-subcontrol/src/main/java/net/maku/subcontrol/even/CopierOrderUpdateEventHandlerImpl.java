package net.maku.subcontrol.even;

import com.cld.message.pubsub.kafka.IKafkaProducer;
import lombok.extern.slf4j.Slf4j;
import net.maku.subcontrol.trader.AbstractApiTrader;
import online.mtapi.mt4.Order;
import online.mtapi.mt4.OrderUpdateEventArgs;

import static online.mtapi.mt4.Op.Buy;
import static online.mtapi.mt4.Op.Sell;

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
        try {
            Order order = orderUpdateEventArgs.Order;
            switch (orderUpdateEventArgs.Action) {
                case PositionOpen:
                    break;
                case PositionClose:
                    break;
                case PositionModify:
                    break;
                case PendingOpen:
                case PendingClose:
                    log.info("此处只处理已经开仓的订单的修改，挂单的修改不做处理。");
                    break;
                case PendingModify:
                    if (orderUpdateEventArgs.Order.Type == Buy || orderUpdateEventArgs.Order.Type == Sell) {
                        log.info("挂单开仓");
                        // 挂单触发后会主动发送一个修改订单的信号，修改了订单类型,这种情况作为一个开仓信号进行发送
    //                execute(OrderChangeTypeEnum.NEW, orderInfo, equity, currency);
                    } else {
                        log.info("挂单修改");
                    }
                    break;
                case PendingFill:
                    log.info("挂单触发");
                    break;
                case Balance:
                    log.info("存取款");
                    break;
                case Credit:
                    log.info("赠金出入金");
                    break;
                default:
                    throw new IllegalStateException("Unexpected value: " + orderUpdateEventArgs.Action);
            }
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
    }
}
