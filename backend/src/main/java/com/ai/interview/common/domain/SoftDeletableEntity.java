package com.ai.interview.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 支持软删除的业务 Entity 基类。
 * <p>Repository 查询需显式带 {@code deletedAt IS NULL} 条件；
 * 不使用 Hibernate {@code @Where} 自动过滤以保持行为可预测。
 */
@MappedSuperclass
@Getter
@Setter
public abstract class SoftDeletableEntity extends BaseEntity {

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Transient
    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void markDeleted() {
        this.deletedAt = Instant.now();
    }
}
