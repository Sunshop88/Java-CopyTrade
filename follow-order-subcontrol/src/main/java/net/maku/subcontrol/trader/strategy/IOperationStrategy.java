package net.maku.subcontrol.trader.strategy;


import net.maku.followcom.pojo.EaOrderInfo;
import net.maku.subcontrol.trader.AbstractApiTrader;
import org.apache.kafka.clients.consumer.ConsumerRecord;

/**
 * MT跟单对象收到信号后，如何处理信号（开仓 平仓 删除 修改）的策略
 */
public interface IOperationStrategy {

    void operate(AbstractApiTrader abstractApiTrader, EaOrderInfo record, int flag);
}