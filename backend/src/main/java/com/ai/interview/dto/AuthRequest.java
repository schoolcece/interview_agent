package com.ai.interview.dto;

import lombok.Data;

/**
 * 用户登录请求体。
 */
@Data
public class AuthRequest {
    /** 登录用户名（对应 users 表 username 字段）。 */
    private String username;

    /** 明文密码，Service 层使用 BCrypt 验证，不存储该值。 */
    private String password;
}
