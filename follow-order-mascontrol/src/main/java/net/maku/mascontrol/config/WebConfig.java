package net.maku.mascontrol.config;

import net.maku.mascontrol.Interceptor.PortRoutingInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private PortRoutingInterceptor portRoutingInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(portRoutingInterceptor)
                .addPathPatterns("/subcontrol/**"); // 这里可以指定要拦截的路径，比如 "/sub/**"
    }
}