package net.maku.mascontrol.util;

import com.cld.message.pubsub.kafka.impl.CldKafkaProducer;
import com.cld.message.pubsub.kafka.properties.Ks;
import com.cld.utils.thread.ThreeStrategyThreadPoolExecutor;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

import java.util.Properties;
import java.util.concurrent.*;

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
        return Executors.newScheduledThreadPool(1000, new CustomizableThreadFactory("定时任务线程池-"));
    }

    @Bean("commonThreadPool")
    ExecutorService commonThreadPool() {
        return Executors.newFixedThreadPool(200, new CustomizableThreadFactory("常规线程池-"));
    }

}
