package com.ai.interview.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 颁发与解析。
 * <p>C 端场景 claim 只含 userId + email，<b>不含 orgId</b>。
 */
@Component
@Slf4j
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    /** 每次调用时从配置字符串动态生成 HMAC 密钥（避免密钥对象被序列化）。 */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成访问令牌（access token）。
     * <p>Payload 中携带 {@code user_id} 和 {@code email}，subject 为 userId 字符串形式。
     *
     * @param userId 用户主键（Long）
     * @param email  用户邮箱（辅助信息，前端可直接解码使用）
     * @return 签名后的 JWT 字符串
     */
    public String generateToken(Long userId, String email) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", email);
        claims.put("user_id", userId);
        return createToken(claims, String.valueOf(userId), jwtExpiration);
    }

    /**
     * 生成刷新令牌（refresh token），有效期比 access token 更长（由 jwt.refresh-expiration 配置）。
     * <p>Payload 中只有 {@code type:"refresh"}，服务端验证时须区分两种 token 类型。
     */
    public String generateRefreshToken(Long userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "refresh");
        return createToken(claims, String.valueOf(userId), refreshExpiration);
    }

    /**
     * 底层 token 构建方法，使用 HS512 算法签名。
     *
     * @param claims      额外携带的 payload 字段
     * @param subject     token subject（这里是 userId 字符串）
     * @param expirationMs token 有效期（毫秒）
     */
    private String createToken(Map<String, Object> claims, String subject, long expirationMs) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    /**
     * 从已验证的 JWT 中提取 userId。
     *
     * @return userId；若 subject 不是合法数字则返回 null
     */
    public Long getUserIdFromJWT(String token) {
        Claims claims = getAllClaimsFromToken(token);
        String subject = claims.getSubject();
        try {
            return Long.parseLong(subject);
        } catch (NumberFormatException ex) {
            log.warn("JWT subject is not a valid user id: {}", subject);
            return null;
        }
    }

    /** 从 JWT 的自定义 claim 中提取 email。 */
    public String getEmailFromJWT(String token) {
        Claims claims = getAllClaimsFromToken(token);
        return (String) claims.get("email");
    }

    /**
     * 验证 JWT 签名与有效期。
     *
     * @param token JWT 字符串
     * @return true 表示签名正确且未过期；false 表示任何验证失败（异常被吞并以 false 返回）
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    /** 解析并返回 JWT 的全部 Claims（含 iss、sub、exp 等标准字段与自定义字段）。 */
    private Claims getAllClaimsFromToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
