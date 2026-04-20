package com.docgen.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 全局 CORS 跨域配置
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${cors.allowed-origins:*}")
    private String allowedOrigins;

    @Value("${cors.allowed-methods:GET,POST,PUT,DELETE,OPTIONS}")
    private String allowedMethods;

    @Value("${cors.allowed-headers:*}")
    private String allowedHeaders;

    @Value("${cors.max-age:3600}")
    private long maxAge;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = allowedOrigins.split(",");
        if (origins.length == 1 && "*".equals(origins[0].trim())) {
            // 当配置为 * 时，不能使用 allowCredentials(true)，改用 allowedOriginPatterns
            registry.addMapping("/**")
                    .allowedOriginPatterns("*")
                    .allowedMethods(allowedMethods.split(","))
                    .allowedHeaders(allowedHeaders.split(","))
                    .allowCredentials(true)
                    .maxAge(maxAge);
        } else {
            registry.addMapping("/**")
                    .allowedOrigins(origins)
                    .allowedMethods(allowedMethods.split(","))
                    .allowedHeaders(allowedHeaders.split(","))
                    .allowCredentials(true)
                    .maxAge(maxAge);
        }
    }

}
