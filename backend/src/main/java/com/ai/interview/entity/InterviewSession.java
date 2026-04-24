package com.ai.interview.entity;

import com.ai.interview.common.domain.BaseEntity;
import com.ai.interview.entity.embeddable.InterviewPlan;
import com.ai.interview.entity.embeddable.SessionConfig;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * 面试会话 - 对应 schema 中 {@code interview_sessions} 表。
 * <p>{@link #version} 用于乐观锁，防止 Java 和 Python 并发修改同一 session。
 */
@Entity
@Table(name = "interview_sessions")
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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "plan", columnDefinition = "json")
    private InterviewPlan plan;

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
