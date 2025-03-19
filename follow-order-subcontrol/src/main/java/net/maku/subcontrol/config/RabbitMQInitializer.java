//package net.maku.subcontrol.config;
//
//
//import jakarta.annotation.PostConstruct;
//import lombok.extern.slf4j.Slf4j;
//import net.maku.subcontrol.config.RabbitMQConfig;
//import org.springframework.amqp.rabbit.core.RabbitAdmin;
//import org.springframework.stereotype.Component;
//
//
//@Component
//@Slf4j
//public class RabbitMQInitializer {
//
//    private final RabbitAdmin rabbitAdmin;
//    private final RabbitMQConfig rabbitMQConfig;
//
//    public RabbitMQInitializer(RabbitAdmin rabbitAdmin, RabbitMQConfig rabbitMQConfig) {
//        this.rabbitAdmin = rabbitAdmin;
//        this.rabbitMQConfig = rabbitMQConfig;
//    }
//
//    @PostConstruct
//    public void initializeRabbitMQ() {
//        try {
//            rabbitAdmin.getRabbitTemplate().execute(channel -> {
//                channel.queueDeclarePassive(RabbitMQConfig.QUEUE_NAME);
//                log.info("Queue already exists: " + RabbitMQConfig.QUEUE_NAME);
//                return null;
//            });
//        } catch (Exception e) {
//            log.info("Queue does not exist, creating: " + RabbitMQConfig.QUEUE_NAME);
//            rabbitAdmin.declareQueue(rabbitMQConfig.myQueue());
//        }
//
//        try {
//            rabbitAdmin.getRabbitTemplate().execute(channel -> {
//                channel.exchangeDeclarePassive(RabbitMQConfig.EXCHANGE_NAME);
//                log.info("Exchange already exists: " + RabbitMQConfig.EXCHANGE_NAME);
//                return null;
//            });
//        } catch (Exception e) {
//            log.info("Exchange does not exist, creating: " + RabbitMQConfig.EXCHANGE_NAME);
//            rabbitAdmin.declareExchange(rabbitMQConfig.myExchange());
//        }
//
//        log.info("Binding queue and exchange...");
//        rabbitAdmin.declareBinding(rabbitMQConfig.binding(rabbitMQConfig.myQueue(), rabbitMQConfig.myExchange()));
//    }
//}