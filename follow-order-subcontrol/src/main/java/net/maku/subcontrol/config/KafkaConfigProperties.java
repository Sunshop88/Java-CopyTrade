package net.maku.subcontrol.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "spring.kafka")
public class KafkaConfigProperties {

    private Map<String, String> groupIdMap;

    public Map<String, String> getGroupIdMap() {
        return groupIdMap;
    }

    public void setGroupIdMap(Map<String, String> groupIdMap) {
        this.groupIdMap = groupIdMap;
    }
}
