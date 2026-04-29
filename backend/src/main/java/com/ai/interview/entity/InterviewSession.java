package com.ai.interview.entity;

import com.ai.interview.common.domain.BaseEntity;
import com.ai.interview.entity.embeddable.SessionConfig;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * 面试会话 - 对应 schema 中 {@code interview_sessions} 表。
 * <p>{@link #version} 用于乐观锁，防止 Java 和 Python 并发修改同一 session。
 * <p>{@code @DynamicUpdate} 使 Hibernate 只将实际变更过的字段写入 UPDATE 语句，
 * 避免 Java 在 startInterview 后保存时用 null 覆盖 Python 已写入的 {@code plan} 字段。
 */
@Entity
@Table(name = "interview_sessions")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class InterviewSession extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "resume_id")
    private Long resumeId;

    @Column(name = "title", length = 255)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private SessionStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config", columnDefinition = "json", nullable = false)
    private SessionConfig config;

    /**
     * Planner 输出的完整 JSON（含 question_sequence、total_turns 等），结构与 Python 一致；
     * 使用 Map 避免强类型 {@code InterviewPlan} 丢弃未知字段导致读库不完整。
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "plan", columnDefinition = "json")
    private Map<String, Object> plan;

    @Column(name = "current_turn", nullable = false)
    private Integer currentTurn;

    @Column(name = "total_turns", nullable = false)
    private Integer totalTurns;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    /**
     * 创建一个处于 PLANNING 状态的面试会话。
     */
    public static InterviewSession create(Long userId, Long resumeId, String title, SessionConfig config) {
        InterviewSession s = new InterviewSession();
        s.userId = userId;
        s.resumeId = resumeId;
        s.title = title;
        s.config = config;
        s.status = SessionStatus.PLANNING;
        s.currentTurn = 0;
        s.totalTurns = config != null && config.getTotalTurns() != null ? config.getTotalTurns() : 10;
        s.version = 0;
        return s;
    }
}
