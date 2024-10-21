package net.maku.subcontrol.config;

import net.maku.subcontrol.Interceptor.ServerIpInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private ServerIpInterceptor serverIpInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 将 ServerIpInterceptor 注册到所有路径下
        registry.addInterceptor(serverIpInterceptor).addPathPatterns("/**");
    }
}