package net.maku.subcontrol;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan(basePackages = {"net.maku.framework","net.maku.followcom", "net.maku.subcontrol"})
@MapperScan(basePackages = {"net.maku.followcom.dao","net.maku.subcontrol.dao"})
@EnableAsync
@EnableScheduling
public class FollowOrderSubcontrolApplication extends SpringBootServletInitializer {
    public static void main(String[] args) {
        SpringApplication.run(FollowOrderSubcontrolApplication.class, args);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(FollowOrderSubcontrolApplication.class);
    }
}
