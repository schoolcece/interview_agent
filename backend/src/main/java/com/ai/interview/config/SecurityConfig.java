package com.ai.interview.config;

import com.ai.interview.security.JwtAuthenticationFilter;
import com.ai.interview.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security 全局配置。
 *
 * <h3>关键决策</h3>
 * <ul>
 *   <li>CORS 统一由此处配置，Controller 上禁止 {@code @CrossOrigin("*")}。</li>
 *   <li>WebSocket 握手路径 {@code /ws/**} 被 Spring Security 放行（permitAll），
 *       鉴权交给 {@link com.ai.interview.security.JwtHandshakeInterceptor} 在握手层处理。
 *       原因：Security Filter Chain 处于 Servlet 层，无法直接管控 WS Upgrade 之后的持久连接，
 *       且 JWT Filter 写入的 ThreadLocal 在 WS 线程模型下不可靠。</li>
 *   <li>SockJS 的 HTTP 传输端点（{@code /ws/**}）同样需要放行，否则握手会 403。</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;

    @Value("${cors.allowed-origins:http://localhost:5173,http://localhost:3000}")
    private String[] allowedOrigins;

    public SecurityConfig(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, rsp, exn) -> {
                            rsp.setContentType("application/json;charset=UTF-8");
                            rsp.setStatus(403);
                            rsp.getWriter().write("{\"error\":\"Authentication required\"}");
                        }))
                .authorizeHttpRequests(auth -> auth
                        // 公开端点
                        .requestMatchers("/auth/login", "/auth/register").permitAll()
                        .requestMatchers("/health").permitAll()
                        // WebSocket 握手路径：鉴权由 JwtHandshakeInterceptor 负责
                        .requestMatchers("/ws/**").permitAll()
                        // 其他一律需要登录
                        .anyRequest().authenticated())
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtTokenProvider),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * HTTP 接口的 CORS 配置。
     * <p>生产环境请将 allowed origins 替换为实际前端域名（从环境变量读取）。
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(Arrays.asList(allowedOrigins));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
