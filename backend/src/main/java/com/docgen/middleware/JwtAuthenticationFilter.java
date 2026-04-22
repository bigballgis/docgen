package com.docgen.middleware;

import com.docgen.config.JwtConfig;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * JWT 认证过滤器
 * 从请求头中提取 Bearer Token，解析并验证后设置安全上下文
 * 如果 token 无效或过期，不清除上下文（让可选认证的接口也能通过）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtConfig jwtConfig;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 从 Authorization 请求头中提取 token
        String token = extractToken(request);

        if (StringUtils.hasText(token)) {
            try {
                // 验证 token 有效性
                if (jwtTokenProvider.validateToken(token)) {
                    // 从 token 中提取用户信息
                    Long userId = jwtTokenProvider.getUserIdFromToken(token);
                    String username = jwtTokenProvider.getUsernameFromToken(token);
                    String role = jwtTokenProvider.getRoleFromToken(token);
                    String tenantId = jwtTokenProvider.getTenantIdFromToken(token);

                    log.debug("JWT 认证成功: userId={}, username={}, role={}, tenantId={}",
                            userId, username, role, tenantId);

                    // 构建用户详情
                    UserDetailsImpl userDetails = new UserDetailsImpl(userId, username, role, tenantId);

                    // 构建权限列表
                    List<SimpleGrantedAuthority> authorities = Collections.singletonList(
                            new SimpleGrantedAuthority("ROLE_" + role.toUpperCase())
                    );

                    // 创建认证令牌并设置到安全上下文
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(userDetails, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception e) {
                // token 无效或过期，不清除上下文，让可选认证的接口也能通过
                log.warn("JWT token 解析失败: {}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 从 Authorization 请求头中提取 Bearer Token
     *
     * @param request HTTP 请求
     * @return JWT token 字符串，如果不存在则返回 null
     */
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
