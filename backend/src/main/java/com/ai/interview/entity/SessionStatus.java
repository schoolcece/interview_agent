package com.ai.interview.entity;

/**
 * 面试会话状态机。详见 {@code docs/ARCHITECTURE.md} §5.2。
 */
public enum SessionStatus {
    PLANNING,
    IN_PROGRESS,
    COMPLETED,
    ABANDONED,
    FAILED
}
