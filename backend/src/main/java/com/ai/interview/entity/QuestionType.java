package com.ai.interview.entity;

/**
 * 题目类型。与 schema {@code question_bank.type} / {@code interview_turns.question_type} 对齐。
 */
public enum QuestionType {
    FACTUAL,
    PROJECT_BASED,
    ALGORITHM,
    SYSTEM_DESIGN,
    BEHAVIORAL
}
