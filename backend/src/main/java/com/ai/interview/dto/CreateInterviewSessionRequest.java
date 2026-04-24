package com.ai.interview.dto;

import com.ai.interview.entity.embeddable.SessionConfig;
import lombok.Data;

@Data
public class CreateInterviewSessionRequest {
    /** 可选：绑定的简历 id；纯练习场景可为 null。 */
    private Long resumeId;

    /** 会话标题，例如 "Java 后端-第 3 场"。 */
    private String title;

    /** 面试配置（领域 / 子主题 / 轮次等）。 */
    private SessionConfig config;
}
