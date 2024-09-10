package net.maku.module;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

@SpringBootApplication
public class FollowOrderSubcontrolApplication extends SpringBootServletInitializer {
    public static void main(String[] args) {
        SpringApplication.run(FollowOrderSubcontrolApplication.class, args);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(FollowOrderSubcontrolApplication.class);
    }
}
