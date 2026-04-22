package com.docgen.middleware;

import com.docgen.entity.AuditLog;
import com.docgen.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDateTime;

/**
 * 审计日志拦截器
 * 在请求完成后（afterCompletion），如果响应状态为 2xx，则记录审计日志
 * 通过 @AuditLog 注解标记需要审计的接口，从请求属性中获取审计信息
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogInterceptor implements HandlerInterceptor {

    /**
     * 审计日志仓库
     */
    private final AuditLogRepository auditLogRepository;

    /**
     * 请求属性中存储审计动作的键名
     */
    private static final String AUDIT_ACTION_ATTR = "auditAction";

    /**
     * 请求属性中存储审计资源的键名
     */
    private static final String AUDIT_RESOURCE_ATTR = "auditResource";

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 检查响应状态是否为 2xx
        int status = response.getStatus();
        if (status < 200 || status >= 300) {
            return;
        }

        // 从请求属性中获取审计信息
        String action = (String) request.getAttribute(AUDIT_ACTION_ATTR);
        String resource = (String) request.getAttribute(AUDIT_RESOURCE_ATTR);

        // 如果没有审计信息，跳过
        if (action == null) {
            return;
        }

        // 获取当前操作用户信息
        String username = "anonymous";
        Long userId = null;
        String tenantId = null;

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserDetailsImpl userDetails) {
            username = userDetails.getUsername();
            userId = userDetails.getId();
            tenantId = userDetails.getTenantId();
        }

        // 记录审计日志
        log.info("审计日志 - 用户: {}, 用户ID: {}, 租户: {}, 动作: {}, 资源: {}, 请求方法: {}, 请求路径: {}",
                username, userId, tenantId, action, resource,
                request.getMethod(), request.getRequestURI());

        // 持久化审计日志到数据库
        try {
            AuditLog entry = new AuditLog();
            entry.setUserId(userId);
            entry.setUsername(username);
            entry.setAction(action);
            entry.setResource(resource);
            entry.setIp(getClientIp(request));
            entry.setUserAgent(request.getHeader("User-Agent"));
            entry.setDetails(request.getMethod() + " " + request.getRequestURI());
            entry.setCreatedAt(LocalDateTime.now());
            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.error("持久化审计日志失败", e);
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isEmpty() && !"unknown".equalsIgnoreCase(realIp)) {
            return realIp;
        }
        return request.getRemoteAddr();
    }
}
