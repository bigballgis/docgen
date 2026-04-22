package com.docgen.middleware;

import com.docgen.entity.Tenant;
import com.docgen.repository.TenantRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;

/**
 * 多租户拦截器
 * 从请求中解析租户ID，优先级：
 * 1. X-Tenant-Id 请求头
 * 2. JWT Token 中的 tenantId
 * 3. 默认值 "default"
 */
@Slf4j
@Component
public class TenantInterceptor implements HandlerInterceptor {

    /**
     * 默认租户ID
     */
    private static final String DEFAULT_TENANT_ID = "default";

    /**
     * 租户ID请求头名称
     */
    private static final String TENANT_HEADER = "X-Tenant-Id";

    /**
     * 请求属性中存储租户ID的键名
     */
    public static final String TENANT_ATTRIBUTE = "tenantId";

    @Autowired
    private TenantRepository tenantRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 优先级1：从请求头获取租户ID
        String tenantId = request.getHeader(TENANT_HEADER);

        // 优先级2：从 JWT 安全上下文中获取租户ID
        if (tenantId == null || tenantId.isEmpty()) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof UserDetailsImpl userDetails) {
                tenantId = userDetails.getTenantId();
            }
        }

        // 优先级3：使用默认租户ID
        if (tenantId == null || tenantId.isEmpty()) {
            tenantId = DEFAULT_TENANT_ID;
        }

        // 校验租户是否存在
        Optional<Tenant> tenant = tenantRepository.findByCode(tenantId);
        if (tenant.isEmpty()) {
            tenantId = DEFAULT_TENANT_ID;
        }

        // 将租户ID设置到请求属性
        request.setAttribute(TENANT_ATTRIBUTE, tenantId);

        // 设置到线程上下文
        TenantContext.setTenantId(tenantId);

        log.debug("多租户拦截器: tenantId={}", tenantId);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 清除线程上下文中的租户ID，防止内存泄漏
        TenantContext.clear();
    }
}
