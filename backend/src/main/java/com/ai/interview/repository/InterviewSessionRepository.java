package com.ai.interview.repository;

import com.ai.interview.entity.InterviewSession;
import com.ai.interview.entity.SessionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 面试 session 数据访问层。
 */
@Repository
public interface InterviewSessionRepository extends JpaRepository<InterviewSession, Long> {

    /** 分页查询指定用户的所有 session，按创建时间倒序。 */
    Page<InterviewSession> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /**
     * 按 ID + 用户 ID 查询 session（兼顾权限校验）。
     * <p>任何操作 session 的 Service 方法都应通过此方法而非 {@code findById}，
     * 以防用户越权操作他人的 session。
     */
    Optional<InterviewSession> findByIdAndUserId(Long id, Long userId);

    /** 统计用户在某个状态下的 session 数量（如统计用户完成了多少场面试）。 */
    long countByUserIdAndStatus(Long userId, SessionStatus status);
}
