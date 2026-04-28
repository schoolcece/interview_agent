package com.ai.interview.repository;

import com.ai.interview.entity.Resume;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 简历数据访问层。
 * <p>所有查询均显式过滤 {@code deletedAt IS NULL}；不使用 Hibernate @Where 注解，
 * 以便在需要时（如管理后台）能查询到已删除记录。
 */
@Repository
public interface ResumeRepository extends JpaRepository<Resume, Long> {

    /** 查询用户的所有未删除简历，按上传时间倒序（最新的排第一）。 */
    List<Resume> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long userId);

    /** 按 ID + 用户 ID 查询未删除简历（兼顾权限校验，防止 A 查 B 的简历）。 */
    Optional<Resume> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);

    /** 查询用户当前活跃的简历（正常情况下每用户只有一条）。 */
    Optional<Resume> findByUserIdAndIsActiveTrueAndDeletedAtIsNull(Long userId);

    /**
     * 将该用户所有未删除简历的 isActive 设为 false。
     * <p>在上传新简历前调用，确保同一时刻只有一条简历处于活跃状态。
     * 使用 JPQL 批量更新，避免先查询再逐条 save 的 N+1 问题。
     */
    @Modifying
    @Query("UPDATE Resume r SET r.isActive = false WHERE r.userId = :userId AND r.deletedAt IS NULL")
    void deactivateAllByUserId(@Param("userId") Long userId);
}
