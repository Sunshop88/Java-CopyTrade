package net.maku.subcontrol.even;

import com.cld.message.pubsub.kafka.IKafkaProducer;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.enums.OrderChangeTypeEnum;
import net.maku.followcom.pojo.EaOrderInfo;
import net.maku.subcontrol.trader.AbstractApiTrader;
import net.maku.subcontrol.util.KafkaTopicUtil;
import online.mtapi.mt4.Order;
import online.mtapi.mt4.OrderUpdateEventArgs;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * mtapi.online 监听MT4账户订单变化
 *
 * @author samson bruce
 */
@Slf4j
public class LeaderOrderUpdateEventHandlerImpl extends OrderUpdateHandler {
    public LeaderOrderUpdateEventHandlerImpl(AbstractApiTrader abstractApiTrader, IKafkaProducer<String, Object> kafkaProducer) {
        super(kafkaProducer);
        this.abstractApiTrader = abstractApiTrader;
        this.leader = this.abstractApiTrader.getTrader();
        this.topic = KafkaTopicUtil.leaderTradeSignalTopic(leader);
    }

    /**
     * 1-开仓
     * 1.1市场执行 PositionOpen
     * 1.2挂单 PendingOpen->挂单触发 PendingFill
     * <p>
     * 2-修改
     * 持仓修改 PositionModify
     * 挂单修改 PendingModify
     * <p>
     * 3-删除
     * 挂单删除 PendingClose
     * <p>
     * 4-平仓
     * 立即平仓 PositionClose
     * 部分平仓 PositionClose->PositionOpen
     * 止损
     * 止赢
     *
     * @param sender               sender
     * @param orderUpdateEventArgs orderUpdateEventArgs
     */
    @Override
    public void invoke(Object sender, OrderUpdateEventArgs orderUpdateEventArgs) {
        if (!running) {
            return;
        }
        Order order = orderUpdateEventArgs.Order;

        String currency = abstractApiTrader.quoteClient.Account().currency;
        switch (orderUpdateEventArgs.Action) {
            case PositionOpen:
            case PendingFill:
                log.info("[MT4喊单者：{}-{}-{}]监听到" + orderUpdateEventArgs.Action + ",订单信息[{}]", leader.getId(), leader.getAccount(), leader.getServerName(), new EaOrderInfo(order));
                scheduledThreadPoolExecutor.submit(() -> {
                    double equity = 0.0;
                    try {
                        equity = abstractApiTrader.quoteClient.AccountEquity();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    send2Copiers(OrderChangeTypeEnum.NEW, order, equity, currency, LocalDateTime.now());
                });
                break;
            case PositionClose:
                log.info("[MT4喊单者：{}-{}-{}]监听到" + orderUpdateEventArgs.Action + ",订单信息[{}]", leader.getId(), leader.getAccount(), leader.getServerName(), new EaOrderInfo(order));
                //持仓时间小于2秒，则延迟一秒发送平仓信号，避免客户测试的时候平仓信号先于开仓信号到达
                int delaySendCloseSignal = delaySendCloseSignal(order.OpenTime, order.CloseTime);
                if (delaySendCloseSignal == 0) {
                    scheduledThreadPoolExecutor.submit(() -> {
                        send2Copiers(OrderChangeTypeEnum.CLOSED, order, 0, currency, LocalDateTime.now());
                    });
                } else {
                    scheduledThreadPoolExecutor.schedule(() -> {
                        send2Copiers(OrderChangeTypeEnum.CLOSED, order, 0, currency, LocalDateTime.now());
                    }, delaySendCloseSignal, TimeUnit.MILLISECONDS);
                }
                break;
            default:
                log.error("Unexpected value: " + orderUpdateEventArgs.Action);
        }
    }
}
