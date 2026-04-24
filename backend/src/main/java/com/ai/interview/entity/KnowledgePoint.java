package com.ai.interview.entity;

import com.ai.interview.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 知识点树节点。Phase 1 只读；Phase 2 Admin 后台才启用写操作。
 */
@Entity
@Table(name = "knowledge_points")
@Getter
@Setter
@NoArgsConstructor
public class KnowledgePoint extends BaseEntity {

    @Column(name = "code", length = 128, nullable = false, unique = true)
    private String code;

    @Column(name = "domain", length = 32, nullable = false)
    private String domain;

    @Column(name = "name", length = 128, nullable = false)
    private String name;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "difficulty_avg", precision = 3, scale = 2)
    private BigDecimal difficultyAvg;

    @Column(name = "weight", precision = 3, scale = 2, nullable = false)
    private BigDecimal weight;

    @Column(name = "embedding_ref", length = 128)
    private String embeddingRef;

    @Column(name = "active", nullable = false)
    private Boolean active;
}
