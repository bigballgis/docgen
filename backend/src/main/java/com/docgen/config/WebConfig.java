package com.docgen.config;

import com.docgen.middleware.AuditLogInterceptor;
import com.docgen.middleware.TenantInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置类
 * 配置 CORS 跨域策略和拦截器注册
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:3000}")
    private String[] allowedOrigins;

    @Autowired
    private TenantInterceptor tenantInterceptor;

    @Autowired
    private AuditLogInterceptor auditLogInterceptor;

    /**
     * 配置 CORS 跨域
     * 允许的来源从配置文件 app.cors.allowed-origins 读取
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                // 从配置文件读取允许的来源
                .allowedOrigins(allowedOrigins)
                // 允许所有请求头
                .allowedHeaders("*")
                // 允许所有 HTTP 方法（GET, POST, PUT, DELETE, OPTIONS 等）
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                // 允许携带凭证（Cookie 等）
                .allowCredentials(false)
                // 预检请求缓存时间：1小时
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantInterceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns("/api/v1/auth/login", "/api/v1/health");
        registry.addInterceptor(auditLogInterceptor)
                .addPathPatterns("/api/v1/**");
    }
}
