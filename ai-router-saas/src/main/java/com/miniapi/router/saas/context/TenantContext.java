package com.miniapi.router.saas.context;

/**
 * 租户上下文持有者。
 * 
 * <p>基于 {@link ThreadLocal} 实现的线程级上下文容器，用于在请求处理链路中
 * 传递当前请求的租户ID、用户ID、角色和链路追踪ID等信息。
 * 
 * <p>典型使用场景：在过滤器中设置上下文，在 Service 层和 Mapper 层中读取，
 * 在请求结束时调用 {@link #clear()} 清理，防止内存泄漏和线程复用导致的数据串扰。
 */
public final class TenantContext {
    private static final ThreadLocal<Long> TENANT_ID = new ThreadLocal<>();   // 当前请求所属的租户ID
    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();     // 当前请求的用户ID
    private static final ThreadLocal<String> ROLE = new ThreadLocal<>();      // 当前用户的角色标识
    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();  // 链路追踪ID，用于日志关联

    /**
     * 私有构造方法，禁止实例化工具类。
     */
    private TenantContext() {}

    /** 设置当前线程的租户ID */
    public static void setTenantId(Long tenantId) { TENANT_ID.set(tenantId); }
    /** 获取当前线程的租户ID */
    public static Long getTenantId() { return TENANT_ID.get(); }
    /** 设置当前线程的用户ID */
    public static void setUserId(Long userId) { USER_ID.set(userId); }
    /** 获取当前线程的用户ID */
    public static Long getUserId() { return USER_ID.get(); }
    /** 设置当前线程的用户角色 */
    public static void setRole(String role) { ROLE.set(role); }
    /** 获取当前线程的用户角色 */
    public static String getRole() { return ROLE.get(); }
    /** 设置当前线程的链路追踪ID */
    public static void setTraceId(String traceId) { TRACE_ID.set(traceId); }
    /** 获取当前线程的链路追踪ID */
    public static String getTraceId() { return TRACE_ID.get(); }

    /**
     * 清除当前线程的所有上下文信息。
     * <p>必须在请求处理结束时调用，以防止线程池复用时出现上下文数据串扰和内存泄漏。
     */
    public static void clear() {
        TENANT_ID.remove();
        USER_ID.remove();
        ROLE.remove();
        TRACE_ID.remove();
    }
}
