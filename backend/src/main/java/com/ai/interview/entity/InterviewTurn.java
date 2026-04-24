package com.ai.interview.entity;

import com.ai.interview.common.domain.BaseEntity;
import com.ai.interview.entity.embeddable.ScoreDetail;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 面试每一轮问答与评分记录。
 * <p><b>Java 侧只读</b>：此表由 Python Agent 独占写入；Java 读取用于统计与展示。
 */
@Entity
@Table(name = "interview_turns")
@Getter
@Setter
@NoArgsConstructor
public class InterviewTurn extends BaseEntity {

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "turn_no", nullable = false)
    private Integer turnNo;

    @Column(name = "question_id")
    private Long questionId;

    @Column(name = "question_text", columnDefinition = "text", nullable = false)
    private String questionText;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", length = 32, nullable = false)
    private QuestionType questionType;

    @Column(name = "ask_audio_path", length = 512)
    private String askAudioPath;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "user_answer_text", columnDefinition = "longtext")
    private String userAnswerText;

    @Column(name = "user_audio_path", length = 512)
    private String userAudioPath;

    @Column(name = "asr_confidence", precision = 4, scale = 3)
    private BigDecimal asrConfidence;

    @Column(name = "score_overall", precision = 4, scale = 2)
    private BigDecimal scoreOverall;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "score_detail", columnDefinition = "json")
    private ScoreDetail scoreDetail;

    @Column(name = "followup_of_turn_id")
    private Long followupOfTurnId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private TurnStatus status;

    @Column(name = "asked_at", nullable = false)
    private Instant askedAt;

    @Column(name = "answered_at")
    private Instant answeredAt;

    @Column(name = "scored_at")
    private Instant scoredAt;
}
