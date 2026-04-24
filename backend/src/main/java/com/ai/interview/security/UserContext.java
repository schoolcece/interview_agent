package com.ai.interview.security;

/**
 * 请求作用域的已登录用户上下文。
 * <p>由 {@link JwtAuthenticationFilter} 在解析 Token 后写入，
 * 由 {@link LoginUserContextService#requireUserId()} 消费。
 * <p>C 端无多租户，不再持有 orgId。
 */
public final class UserContext {

    private static final ThreadLocal<Long> userIdHolder = new ThreadLocal<>();

    private UserContext() {
    }

    public static void setUserId(Long userId) {
        userIdHolder.set(userId);
    }

    public static Long getUserId() {
        return userIdHolder.get();
    }

    public static void clear() {
        userIdHolder.remove();
    }
}
