package com.ai.interview.entity;

import com.ai.interview.common.domain.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 用户 - 对应 schema 中 {@code users} 表。
 * <p>字段语义见 {@code V1__init_schema.sql} 第 1 节。
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User extends SoftDeletableEntity {

    @Column(name = "username", length = 64, nullable = false, unique = true)
    private String username;

    @Column(name = "email", length = 128, nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", length = 128, nullable = false)
    private String passwordHash;

    @Column(name = "display_name", length = 64)
    private String displayName;

    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    @Column(name = "target_domain", length = 32)
    private String targetDomain;

    @Column(name = "experience_level", length = 16)
    private String experienceLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 16, nullable = false)
    private UserRole role;

    @Column(name = "active", nullable = false)
    private Boolean active;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    /**
     * 注册工厂方法：保证必填字段齐全。
     * 调用方（AuthService）负责传入已散列后的 passwordHash。
     */
    public static User register(String username, String email, String passwordHash,
                                 String displayName, String targetDomain, String experienceLevel) {
        User u = new User();
        u.username = username;
        u.email = email;
        u.passwordHash = passwordHash;
        u.displayName = displayName;
        u.targetDomain = targetDomain;
        u.experienceLevel = experienceLevel;
        u.role = UserRole.USER;
        u.active = Boolean.TRUE;
        return u;
    }

    public void touchLogin() {
        this.lastLoginAt = Instant.now();
    }
}
