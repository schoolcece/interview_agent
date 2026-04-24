package com.ai.interview.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 错题本。
 * <p>注意：此表 schema 使用 {@code first_seen_at} + {@code updated_at}（无 {@code created_at}），
 * 因此 <b>不继承</b> {@link com.ai.interview.common.domain.BaseEntity}。
 */
@Entity
@Table(name = "error_problems")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class ErrorProblem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, updatable = false)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "question_id")
    private Long questionId;

    @Column(name = "question_snapshot", columnDefinition = "text")
    private String questionSnapshot;

    @Column(name = "first_turn_id")
    private Long firstTurnId;

    @Column(name = "latest_turn_id")
    private Long latestTurnId;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;

    @Column(name = "last_score", precision = 4, scale = 2)
    private BigDecimal lastScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private ErrorStatus status;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "first_seen_at", nullable = false, updatable = false)
    private Instant firstSeenAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (firstSeenAt == null) {
            firstSeenAt = Instant.now();
        }
        if (attemptCount == null) {
            attemptCount = 1;
        }
        if (status == null) {
            status = ErrorStatus.OPEN;
        }
    }
}
