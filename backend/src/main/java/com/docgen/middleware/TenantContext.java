package com.docgen.middleware;

/**
 * 租户上下文工具类
 * 使用 ThreadLocal 存储当前线程的租户ID
 * 在多租户场景下，方便在 Service 层获取当前租户信息
 */
public class TenantContext {

    /**
     * 线程本地变量，存储当前租户ID
     */
    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    /**
     * 设置当前线程的租户ID
     *
     * @param tenantId 租户ID
     */
    public static void setTenantId(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    /**
     * 获取当前线程的租户ID
     *
     * @return 当前租户ID，如果未设置则返回 null
     */
    public static String getTenantId() {
        return CURRENT_TENANT.get();
    }

    /**
     * 清除当前线程的租户ID
     * 在请求结束后调用，防止线程池复用导致的数据泄漏
     */
    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
