package net.maku.framework.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * Redis配置
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */

@Configuration
@EnableConfigurationProperties
public class RedisConfig {

    @Bean
    @Primary
    @ConfigurationProperties(prefix = "spring.data.redis.redis1")
    public RedisProperties redisProperties1() {
        return new RedisProperties();
    }

//    @Bean
//    @ConfigurationProperties(prefix = "spring.data.redis.redis2")
//    public RedisProperties redisProperties2() {
//        return new RedisProperties();
//    }


    @Bean
    @Primary
    public RedisConnectionFactory redisConnectionFactory1(@Qualifier("redisProperties1") RedisProperties redisProperties) {
        return createLettuceConnectionFactory(redisProperties);
    }

//    @Bean
//    public RedisConnectionFactory redisConnectionFactory2(@Qualifier("redisProperties2") RedisProperties redisProperties) {
//        return createLettuceConnectionFactory(redisProperties);
//    }

    private LettuceConnectionFactory createLettuceConnectionFactory(RedisProperties redisProperties) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisProperties.getHost());
        config.setPort(redisProperties.getPort());
        config.setDatabase(redisProperties.getDatabase());  // 设置 database
        config.setPassword(redisProperties.getPassword());
        return new LettuceConnectionFactory(config);
    }

    @Bean
    @Primary
    public RedisTemplate<String, Object> redisTemplate1(@Qualifier("redisConnectionFactory1") RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        configureSerialization(template,factory);
        return template;
    }

//    @Bean
//    public RedisTemplate<String, Object> redisTemplate2(@Qualifier("redisConnectionFactory2") RedisConnectionFactory factory) {
//        RedisTemplate<String, Object> template = new RedisTemplate<>();
//        template.setConnectionFactory(factory);
//        configureSerialization(template,factory);
//        return template;
//    }

    @Bean
    @Primary
    public RedissonClient redissonClient1(@Qualifier("redisProperties1") RedisProperties redisProperties) {
        return createRedissonClient(redisProperties);
    }

//    @Bean
//    public RedissonClient redissonClient2(@Qualifier("redisProperties2") RedisProperties redisProperties) {
//        return createRedissonClient(redisProperties);
//    }

    private RedissonClient createRedissonClient(RedisProperties redisProperties) {
        Config config = new Config();

        // 使用单机模式
        config.useSingleServer()
                .setAddress("redis://" + redisProperties.getHost() + ":" + redisProperties.getPort())
                .setDatabase(redisProperties.getDatabase());

        // 如果有密码，设置密码
        if (redisProperties.getPassword() != null) {
            config.useSingleServer().setPassword(redisProperties.getPassword());
        }

        // 连接池配置（可选）
        config.useSingleServer()
                .setConnectionPoolSize(6000)  // 连接池大小
                .setConnectTimeout(10000)    // 连接超时时间
                .setIdleConnectionTimeout(10000);   // 空闲连接超时时间

        return Redisson.create(config);
    }

    private void configureSerialization(RedisTemplate<String, Object> template,RedisConnectionFactory factory) {

        // Key HashKey使用String序列化
        template.setKeySerializer(RedisSerializer.string());
        template.setHashKeySerializer(RedisSerializer.string());

        // Value HashValue使用Json序列化
        template.setValueSerializer(genericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(genericJackson2JsonRedisSerializer());

        template.setConnectionFactory(factory);

        template.afterPropertiesSet();
    }

    public GenericJackson2JsonRedisSerializer genericJackson2JsonRedisSerializer() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance, ObjectMapper.DefaultTyping.NON_FINAL);

        return new GenericJackson2JsonRedisSerializer(objectMapper);
    }
}