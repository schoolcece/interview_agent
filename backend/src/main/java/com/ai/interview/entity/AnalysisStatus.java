package com.ai.interview.entity;

/**
 * 简历异步解析状态。与 schema 中 {@code resumes.analysis_status} 对齐。
 */
public enum AnalysisStatus {
    PENDING,
    PARSING,
    PARSED,
    FAILED
}
