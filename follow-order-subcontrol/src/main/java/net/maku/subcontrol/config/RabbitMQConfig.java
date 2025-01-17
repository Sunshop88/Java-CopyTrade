//package net.maku.subcontrol.config;
//
//import jakarta.annotation.PostConstruct;
//import org.springframework.amqp.core.Binding;
//import org.springframework.amqp.core.BindingBuilder;
//import org.springframework.amqp.core.DirectExchange;
//import org.springframework.amqp.core.Queue;
//import org.springframework.amqp.rabbit.connection.ConnectionFactory;
//import org.springframework.amqp.rabbit.core.RabbitAdmin;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//
//@Configuration
//public class RabbitMQConfig {
//
//    public static final String EXCHANGE_NAME = "order_update_queue_follow";
//    public static final String ROUTING_KEY = ""; // 默认路由键为空字符串
//    public static final String QUEUE_NAME = "order_follow_queue";
//
//    @Bean
//    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
//        // 用于管理 RabbitMQ 的 admin 工具
//        return new RabbitAdmin(connectionFactory);
//    }
//
//    @Bean
//    public Queue myQueue() {
//        // 定义队列
//        return new Queue(QUEUE_NAME, true, false, false);
//    }
//
//    @Bean
//    public DirectExchange myExchange() {
//        // 定义交换机
//        return new DirectExchange(EXCHANGE_NAME, true, false);
//    }
//
//    @Bean
//    public Binding binding(Queue myQueue, DirectExchange myExchange) {
//        // 定义绑定关系
//        return BindingBuilder.bind(myQueue).to(myExchange).with(ROUTING_KEY);
//    }
//}
