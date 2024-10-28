package net.maku.followcom.util;

import com.cld.message.pubsub.kafka.impl.CldKafkaProducer;
import com.cld.message.pubsub.kafka.properties.Ks;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
public class CtAppConfig {

    @Bean("cachedThreadPool")
    ExecutorService newCachedThreadPool() {
        return Executors.newCachedThreadPool(new CustomizableThreadFactory("kafka消费线程池-"));
    }

    @Bean("fixedThreadPool")
    ExecutorService fixedThreadPool() {
        return Executors.newFixedThreadPool(200, new CustomizableThreadFactory("同步任务线程池-"));
    }

    @Bean("scheduledExecutorService")
    ScheduledExecutorService scheduledExecutorService() {
        return Executors.newScheduledThreadPool(200, new CustomizableThreadFactory("定时任务线程池-"));
    }

    @Bean("commonThreadPool")
    ExecutorService commonThreadPool() {
        return Executors.newFixedThreadPool(200, new CustomizableThreadFactory("常规线程池-"));
    }

    @Bean
    @ConfigurationProperties(prefix = "cld.kafka")
    Ks ks() {
        return new Ks();
    }

    @Bean
    AdminClient adminClient(Ks ks) {
        Properties properties = new Properties();
        properties.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, ks.getServers());
        return AdminClient.create(properties);
    }

    @Bean(name = "cldKafkaProducer_String_Object")
    CldKafkaProducer<String, Object> cldKafkaProducer(Ks ks) {
        return new CldKafkaProducer<>(ks);
    }

}
