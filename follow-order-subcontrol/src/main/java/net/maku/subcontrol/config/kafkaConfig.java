package net.maku.subcontrol.config;

import com.cld.message.pubsub.kafka.impl.CldKafkaProducer;
import com.cld.message.pubsub.kafka.properties.Ks;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

@Configuration
public class kafkaConfig {

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
