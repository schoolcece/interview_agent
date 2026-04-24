package com.ai.interview.entity.embeddable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 简历解析后的结构化 JSON，映射到 {@code resumes.parsed_content}。
 * <p>字段命名与 schema 注释中的推荐结构对齐。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
    public static class Education {
        private String school;
        private String degree;
        private String period;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
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
    public static class Project {
        private String name;
        private List<String> stack;
        private String role;
        private String summary;
        private List<String> highlights;
    }
}
