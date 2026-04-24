package com.ai.interview.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

/**
 * WebSocket 握手鉴权拦截器。
 *
 * <h3>为什么要在握手阶段验证，而不是在消息阶段验证？</h3>
 * <ul>
 *   <li>握手是 HTTP Upgrade 请求，此时 Spring Security 的过滤器链已经执行过；
 *       但 WebSocket 握手路径被 {@code SecurityConfig} 放行（permitAll），
 *       JWT Filter 不会把 {@code UserContext} 塞进去——握手完成后，
 *       WebSocket 连接就彻底脱离了 HTTP 请求周期（ThreadLocal 失效）。</li>
 *   <li>因此必须在握手阶段主动解析 token，并将 userId 存到
 *       {@link org.springframework.web.socket.WebSocketSession#getAttributes()}，
 *       后续消息处理器从 session attributes 读取，与 ThreadLocal 解耦。</li>
 * </ul>
 *
 * <h3>为什么放 query string 而不是 header？</h3>
 * <ul>
 *   <li>浏览器原生 WebSocket API 不支持自定义请求头；
 *       SockJS 同样无法在握手 URL 里附加 Authorization 头。</li>
 *   <li>query string 的安全代价：token 会出现在服务器 access log 中；
 *       缓解措施：1）access token 有效期短（24h）；2）生产环境过滤 log 中的 token 字段。</li>
 *   <li>替代方案：连接建立后通过第一条消息鉴权（两步握手），但实现复杂且有竞态，Phase 0 不采用。</li>
 * </ul>
 */
@Slf4j
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    /** 存入 session attributes 的 key，Handler 端从这里读 userId。 */
    public static final String USER_ID_ATTR = "userId";

    private final JwtTokenProvider tokenProvider;

    public JwtHandshakeInterceptor(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    /**
     * 握手前调用：解析并校验 JWT，将 userId 写入 attributes。
     *
     * @return {@code true} 允许握手继续；{@code false} 中断握手（返回 401）
     */
    @Override
    public boolean beforeHandshake(@NonNull ServerHttpRequest request,
                                   @NonNull ServerHttpResponse response,
                                   @NonNull WebSocketHandler wsHandler,
                                   @NonNull Map<String, Object> attributes) {

        String token = extractToken(request);

        if (token == null) {
            log.warn("WS handshake rejected: no token in query string. uri={}", request.getURI());
            response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            return false;
        }

        if (!tokenProvider.validateToken(token)) {
            log.warn("WS handshake rejected: invalid token. uri={}", request.getURI());
            response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            return false;
        }

        Long userId = tokenProvider.getUserIdFromJWT(token);
        if (userId == null) {
            log.warn("WS handshake rejected: token has no valid user_id");
            response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            return false;
        }

        attributes.put(USER_ID_ATTR, userId);
        log.debug("WS handshake approved: userId={}", userId);
        return true;
    }

    @Override
    public void afterHandshake(@NonNull ServerHttpRequest request,
                               @NonNull ServerHttpResponse response,
                               @NonNull WebSocketHandler wsHandler,
                               Exception exception) {
        // 握手后无需额外处理
    }

    /**
     * 从请求 URI 的 query string 提取 token 参数。
     * <p>示例：{@code ws://host/ws/interview?token=eyJhb...}
     */
    private String extractToken(ServerHttpRequest request) {
        List<String> tokens = UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams()
                .get("token");
        return (tokens != null && !tokens.isEmpty()) ? tokens.get(0) : null;
    }
}
