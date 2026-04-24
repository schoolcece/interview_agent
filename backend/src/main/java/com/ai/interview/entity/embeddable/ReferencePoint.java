package com.ai.interview.entity.embeddable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 事实类题目的采分点。对应 {@code question_bank.reference_points} 数组元素。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReferencePoint {
    private String point;
    private BigDecimal weight;
    private List<String> keywords;
}
