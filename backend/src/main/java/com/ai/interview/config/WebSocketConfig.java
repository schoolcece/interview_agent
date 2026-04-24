package com.ai.interview.config;

import com.ai.interview.security.JwtHandshakeInterceptor;
import com.ai.interview.security.JwtTokenProvider;
import com.ai.interview.websocket.InterviewWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置。
 *
 * <h3>关键点</h3>
 * <ol>
 *   <li>{@link JwtHandshakeInterceptor} 在 HTTP Upgrade 握手时验证 JWT，
 *       拒绝未携带 token 或 token 非法的连接。</li>
 *   <li>{@code setAllowedOriginPatterns} 取代旧的 {@code setAllowedOrigins("*")}。
 *       前者在 Spring WebSocket 里才真正支持带凭证的 CORS；"*" 与凭证请求不兼容。
 *       生产应替换为具体域名，如 {@code https://your-domain.com}。</li>
 *   <li>{@code withSockJS()} 保留，以兼容不支持原生 WS 的环境（旧浏览器、某些代理）。</li>
 * </ol>
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final InterviewWebSocketHandler interviewWebSocketHandler;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${cors.allowed-origins:http://localhost:5173,http://localhost:3000}")
    private String[] allowedOrigins;

    public WebSocketConfig(InterviewWebSocketHandler interviewWebSocketHandler,
                           JwtTokenProvider jwtTokenProvider) {
        this.interviewWebSocketHandler = interviewWebSocketHandler;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(interviewWebSocketHandler, "/ws/interview")
                .addInterceptors(new JwtHandshakeInterceptor(jwtTokenProvider))
                .setAllowedOriginPatterns(allowedOrigins)
                .withSockJS();
    }
}
