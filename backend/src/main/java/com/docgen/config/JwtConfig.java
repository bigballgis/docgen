package com.docgen.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置类
 * 从 application.yml 中读取 jwt.* 配置项
 */
@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtConfig {

    /**
     * JWT 签名密钥
     */
    private String secret;

    /**
     * JWT 过期时间（毫秒），默认 24 小时
     */
    private long expiration = 86400000L;
}
