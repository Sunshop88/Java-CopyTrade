package net.maku.subcontrol;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger配置
 *
 * @author 阿沐  babamu@126.com
 */
@Configuration
public class SwaggerConfig {



    @Bean
    public GroupedOpenApi memberApi() {
        String[] paths = {"/**"};
        String[] packagedToMatch = {"net.maku.subcontrol"};
        return GroupedOpenApi.builder()
                .group("4")
                .displayName("Subcontrol API")
                .pathsToMatch(paths)
                .packagesToScan(packagedToMatch).build();
    }
}