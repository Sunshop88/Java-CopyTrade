package net.maku.subcontrol.service;


import org.apache.kafka.clients.consumer.ConsumerRecord;

/**
 * MT跟单对象收到信号后，如何处理信号（开仓 平仓 删除 修改）的策略
 */
public interface IOperationStrategy {
    /**
     * 处理KAFKA发送来的信号
     * @param record ConsumerRecord
     * @param retry 处理次数
     */
    void operate(ConsumerRecord<String, Object> record, int retry);
}