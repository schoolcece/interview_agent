package com.ai.interview.entity.embeddable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Planner Agent 输出的题目计划 JSON，映射到 {@code interview_sessions.plan}。
 * <p>结构由 Python 侧 Planner 决定；此处保留宽松结构以支持后续演进。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewPlan {

    private String strategy;                       // "weakness_first" / "topic_based" / ...
    private List<Map<String, Object>> candidates;  // [{questionId, kpCodes, difficulty, ...}]
    private Map<String, Object> metadata;
}
