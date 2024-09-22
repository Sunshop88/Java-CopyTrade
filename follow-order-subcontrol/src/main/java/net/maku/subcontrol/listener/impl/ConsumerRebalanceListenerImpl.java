package net.maku.subcontrol.listener.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;

import java.util.Collection;

/**
 * ConsumerRebalanceListener,开始消费喊单者的订单，从最近的订单开始消费。
 */
@Slf4j
public class ConsumerRebalanceListenerImpl<K,V> implements ConsumerRebalanceListener {
    private final KafkaConsumer<K, V> consumer;

    public ConsumerRebalanceListenerImpl(KafkaConsumer<K, V> consumer) {
        this.consumer = consumer;
    }

    @Override
    public void onPartitionsRevoked(Collection<TopicPartition> collection) {
        log.debug("onPartitionsRevoked");
    }

    @Override
    public void onPartitionsAssigned(Collection<TopicPartition> collection) {
        log.debug("onPartitionsAssigned");
        //将kafka该主题下该group.id的offset量移动到末尾，避免消费kafka主题下的一些存量消息。
        consumer.seekToEnd(collection);
    }

    @Override
    public void onPartitionsLost(Collection<TopicPartition> partitions) {
        log.debug("onPartitionsLost");
    }
}
