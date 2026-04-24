package com.ai.interview.entity.embeddable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Reflection 阶段产生的下一步建议。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NextSuggestion {
    /** practice_kp / next_interview / learn_resource */
    private String type;
    private String kpCode;
    private String reason;
    private Map<String, Object> config;
}
