package com.ai.interview.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * JWT 认证过滤器，挂载在 Spring Security 过滤器链中，每个请求只执行一次。
 *
 * <p>执行流程：
 * <ol>
 *   <li>从请求 Header 的 {@code Authorization: Bearer <token>} 中提取 JWT。</li>
 *   <li>验证 JWT 签名与有效期；验证通过后将 userId 写入 {@link UserContext}（ThreadLocal）
 *       并注入 {@link org.springframework.security.core.context.SecurityContext}。</li>
 *   <li>无论成功与否，都放行请求（不直接返回 401），具体的鉴权决策由 Spring Security
 *       的 {@code authorizeHttpRequests} 规则负责。</li>
 *   <li>请求处理完成后（finally 块）清理 ThreadLocal 和 SecurityContext，
 *       避免线程池复用导致数据串行。</li>
 * </ol>
 */
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                Long userId = tokenProvider.getUserIdFromJWT(jwt);
                if (userId != null) {
                    // 写入 ThreadLocal，供 Service 层的 LoginUserContextService.requireUserId() 读取
                    UserContext.setUserId(userId);

                    // 注入 SecurityContext，使 Spring Security 的 @PreAuthorize 等注解生效
                    Authentication authentication = new UsernamePasswordAuthenticationToken(
                            userId, null, Collections.emptyList());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        } catch (Exception ex) {
            log.warn("Could not set user authentication: {}", ex.getMessage());
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            // 必须在 finally 中清理，防止 Tomcat 线程池复用时携带上一个请求的用户信息
            UserContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * 从 HTTP 请求头中提取 Bearer Token。
     *
     * @return JWT 字符串；若 Header 不存在或格式不正确则返回 null
     */
    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
