package com.docgen.service;

import com.docgen.entity.User;
import com.docgen.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 认证服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // JWT 密钥
    private final SecretKey secretKey = Keys.secretKeyFor(SignatureAlgorithm.HS256);

    // JWT 过期时间（24小时）
    private static final long EXPIRATION_TIME = 24 * 60 * 60 * 1000;

    /**
     * 用户登录
     */
    public Map<String, Object> login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new RuntimeException("密码错误");
        }

        String token = generateToken(user);

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("user", user);

        return result;
    }

    /**
     * 生成 JWT token
     */
    private String generateToken(User user) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + EXPIRATION_TIME);

        return Jwts.builder()
                .setSubject(user.getUsername())
                .setIssuedAt(now)
                .setExpiration(expiration)
                .claim("userId", user.getId())
                .claim("role", user.getRole())
                .claim("tenantId", user.getTenantId())
                .signWith(secretKey)
                .compact();
    }

    /**
     * 初始化默认管理员账户
     */
    public void initDefaultAdmin() {
        if (userRepository.findByUsername("admin").isEmpty()) {
            User admin = new User(
                    "admin",
                    passwordEncoder.encode("admin123"),
                    "admin"
            );
            userRepository.save(admin);
            log.info("默认管理员账户已创建: admin / admin123");
        }
    }

}
