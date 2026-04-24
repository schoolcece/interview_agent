package com.ai.interview.entity;

import com.ai.interview.common.domain.SoftDeletableEntity;
import com.ai.interview.entity.embeddable.ParsedResume;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

/**
 * 简历 - 对应 schema 中 {@code resumes} 表。
 * <p>关键注意：
 * <ul>
 *   <li>{@link #parsedContent} / {@link #mentionedKps} 由 Python 解析后写入，Java 侧只读。</li>
 *   <li>同一用户同时只允许一条 {@code is_active = TRUE}，由 Service 层保证。</li>
 * </ul>
 */
@Entity
@Table(name = "resumes")
@Getter
@Setter
@NoArgsConstructor
public class Resume extends SoftDeletableEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    @Column(name = "file_path", length = 512, nullable = false)
    private String filePath;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "raw_text", columnDefinition = "longtext")
    private String rawText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parsed_content", columnDefinition = "json")
    private ParsedResume parsedContent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "mentioned_kps", columnDefinition = "json")
    private List<String> mentionedKps;

    @Column(name = "embedding_ref", length = 128)
    private String embeddingRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_status", length = 32, nullable = false)
    private AnalysisStatus analysisStatus;

    @Column(name = "analysis_error", columnDefinition = "text")
    private String analysisError;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;
}
