package com.ai.interview.entity.embeddable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 简历解析后的结构化 JSON，映射到 {@code resumes.parsed_content}。
 * <p>字段命名与 Python Worker 的输出结构保持一致。
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} 确保 Python 输出额外字段时
 * 不会导致 Hibernate 反序列化失败，起兜底防御作用。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ParsedResume {

    private Basics basics;
    private List<Education> education;
    private List<Experience> experiences;
    private List<Project> projects;
    private List<String> skills;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Basics {
        private String name;
        private String email;
        private String phone;
        private String target;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Education {
        private String school;
        private String degree;
        private String period;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Experience {
        private String company;
        private String role;
        private String period;
        private String summary;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Project {
        private String name;
        private List<String> stack;
        private String role;
        private String summary;
        private List<String> highlights;
    }
}
