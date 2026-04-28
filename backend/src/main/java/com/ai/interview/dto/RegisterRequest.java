package com.ai.interview.dto;

import lombok.Data;

/**
 * 用户注册请求体。
 */
@Data
public class RegisterRequest {
    /** 登录用户名，全局唯一，长度限制 64 字符。 */
    private String username;

    /** 邮箱地址，全局唯一，用于找回密码等。 */
    private String email;

    /** 明文密码，Service 层使用 BCrypt 散列后存储，绝不持久化原始密码。 */
    private String password;

    /** 昵称/显示名，前端展示用（可与 username 不同）。 */
    private String displayName;

    /** 求职目标领域，如 "backend"、"frontend"、"algorithm"，用于个性化出题。 */
    private String targetDomain;

    /** 经验级别，如 "junior"、"mid"、"senior"，影响题目难度策略。 */
    private String experienceLevel;
}
