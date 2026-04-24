package com.ai.interview.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * 业务层获取当前登录用户 id 的入口。
 * <p>所有 Service 首行调用 {@link #requireUserId()}，从而保证"没登录则拒绝"。
 */
@Service
public class LoginUserContextService {

    public Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new AccessDeniedException("Unauthorized");
        }
        return userId;
    }
}
