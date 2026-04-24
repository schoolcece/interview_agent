package com.ai.interview.entity;

import com.ai.interview.entity.embeddable.KpSummaryItem;
import com.ai.interview.entity.embeddable.NextSuggestion;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 整场面试评估报告（含 Reflection 内容）。
 * <p>schema 仅有 {@code generated_at}，不继承 BaseEntity。
 * <p><b>Java 侧只读</b>：由 Python Evaluator Agent 写入。
 */
@Entity
@Table(name = "evaluation_reports")
@Getter
@Setter
@NoArgsConstructor
public class EvaluationReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, updatable = false)
    private Long id;

    @Column(name = "session_id", nullable = false, unique = true)
    private Long sessionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "overall_score", precision = 4, scale = 2)
    private BigDecimal overallScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dimension_scores", columnDefinition = "json")
    private Map<String, BigDecimal> dimensionScores;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "strengths", columnDefinition = "json")
    private List<String> strengths;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "weaknesses", columnDefinition = "json")
    private List<String> weaknesses;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "kp_summary", columnDefinition = "json")
    private List<KpSummaryItem> kpSummary;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "professional_comment", columnDefinition = "longtext")
    private String professionalComment;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "improvement_plan", columnDefinition = "longtext")
    private String improvementPlan;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "next_suggestions", columnDefinition = "json")
    private List<NextSuggestion> nextSuggestions;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;
}
