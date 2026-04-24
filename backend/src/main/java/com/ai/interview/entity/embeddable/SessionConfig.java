package com.ai.interview.entity.embeddable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 面试会话配置 JSON，映射到 {@code interview_sessions.config}。
 * <p>字段含义参考 {@code V1__init_schema.sql} 第 7 节注释。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionConfig {

    private String domain;                   // backend / frontend / ai / bigdata / cloudnative
    private List<String> subTopics;
    private List<Integer> difficultyRange;   // [min, max]
    private Integer totalTurns;
    private String scoringMode;              // standard / lenient / strict
    private Boolean voiceEnabled;
    private Boolean includeAlgorithm;
    private String language;                 // zh-CN / en-US
}
