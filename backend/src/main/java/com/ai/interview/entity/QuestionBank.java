package com.ai.interview.entity;

import com.ai.interview.common.domain.SoftDeletableEntity;
import com.ai.interview.entity.embeddable.ReferencePoint;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 题目库 - Phase 1 <b>只读</b>（Phase 2 Admin 后台才启用写操作）。
 */
@Entity
@Table(name = "question_bank")
@Getter
@Setter
@NoArgsConstructor
public class QuestionBank extends SoftDeletableEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 32, nullable = false)
    private QuestionType type;

    @Column(name = "domain", length = 32, nullable = false)
    private String domain;

    @Column(name = "difficulty", nullable = false)
    private Integer difficulty;

    @Column(name = "stem", columnDefinition = "text", nullable = false)
    private String stem;

    @Column(name = "stem_hash", length = 64, nullable = false, unique = true)
    private String stemHash;

    @Column(name = "standard_answer", columnDefinition = "text")
    private String standardAnswer;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reference_points", columnDefinition = "json")
    private List<ReferencePoint> referencePoints;

    @Column(name = "rubric_id")
    private Long rubricId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scoring_mode", length = 16, nullable = false)
    private QuestionScoringMode scoringMode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "meta", columnDefinition = "json")
    private Map<String, Object> meta;

    @Column(name = "source", length = 32, nullable = false)
    private String source;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private QuestionBankStatus status;

    @Column(name = "usage_count", nullable = false)
    private Integer usageCount;

    @Column(name = "avg_score", precision = 4, scale = 2)
    private BigDecimal avgScore;

    @Column(name = "embedding_ref", length = 128)
    private String embeddingRef;

    @Column(name = "created_by")
    private Long createdBy;
}
