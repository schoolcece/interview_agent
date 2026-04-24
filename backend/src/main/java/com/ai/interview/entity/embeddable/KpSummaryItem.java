package com.ai.interview.entity.embeddable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 整场面试中单个知识点的聚合摘要，放入 {@code evaluation_reports.kp_summary}。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KpSummaryItem {
    private String kpCode;
    private Integer hits;
    private Integer misses;
    private Integer partial;
    private BigDecimal deltaMastery;
}
