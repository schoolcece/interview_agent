package com.ai.interview.entity;

import com.ai.interview.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 用户 - 知识点掌握度（贝叶斯 Beta 分布 + 间隔复习）。
 * <p>详见 ARCHITECTURE.md §5.4。
 */
@Entity
@Table(name = "user_knowledge_mastery")
@Getter
@Setter
@NoArgsConstructor
public class UserKnowledgeMastery extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "knowledge_point_id", nullable = false)
    private Long knowledgePointId;

    @Column(name = "alpha", precision = 8, scale = 3, nullable = false)
    private BigDecimal alpha;

    @Column(name = "beta", precision = 8, scale = 3, nullable = false)
    private BigDecimal beta;

    /** MySQL STORED 生成列: alpha / (alpha+beta)，Java 侧只读。 */
    @Column(name = "mastery_mean", precision = 6, scale = 4,
            insertable = false, updatable = false)
    private BigDecimal masteryMean;

    @Column(name = "observations", nullable = false)
    private Integer observations;

    @Column(name = "last_score", precision = 4, scale = 2)
    private BigDecimal lastScore;

    @Column(name = "last_practiced_at")
    private Instant lastPracticedAt;

    @Column(name = "next_review_at")
    private Instant nextReviewAt;

    @Column(name = "review_interval_days", nullable = false)
    private Integer reviewIntervalDays;
}
