package net.maku.mascontrol.websocket;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

@Configuration
public class WebSocketEndpointConfig {

    /**
     * ServerEndpointExporter 是用来扫描并注册所有携带 @ServerEndpoint 注解的实例
     */
    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }
}
