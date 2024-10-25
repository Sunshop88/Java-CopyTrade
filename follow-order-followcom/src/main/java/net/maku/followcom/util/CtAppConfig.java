package net.maku.followcom.util;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

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

}
