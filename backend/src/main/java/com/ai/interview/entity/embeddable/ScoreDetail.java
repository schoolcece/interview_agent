package com.ai.interview.entity.embeddable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 单轮评分详情 JSON。对应 {@code interview_turns.score_detail}。
 * <p>事实类 (factual) 与开放类 (open) 共用此结构，详见 ARCHITECTURE.md §4.4。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreDetail {

    /** factual / open */
    private String scoringType;

    /** 维度评分，key 如 correctness / depth / expression。 */
    private Map<String, DimensionScore> dimensions;

    /** 综合分 [0, 5]。 */
    private BigDecimal overallScore;

    /** 本轮涉及的知识点及命中情况。 */
    private List<KpJudgement> knowledgePoints;

    private Feedback feedback;

    /** 开放题独有的 rubric 分项得分，事实题为 null。 */
    private Map<String, BigDecimal> rubricScores;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DimensionScore {
        private Integer score;
        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KpJudgement {
        private String kpCode;
        /** hit / partial / miss */
        private String status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Feedback {
        private List<String> strengths;
        private List<String> weaknesses;
        private List<String> suggestedFollowupKps;
    }
}
