package com.ai.interview.repository;

import com.ai.interview.entity.AsyncJob;
import com.ai.interview.entity.JobType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 异步任务数据访问层。
 * <p>Java 侧只负责创建任务（PENDING 状态）；Python Worker 通过直连 DB 查询并领取任务，
 * 执行后更新 status / result / error_message 字段。
 */
@Repository
public interface AsyncJobRepository extends JpaRepository<AsyncJob, Long> {

    /** 按 ID + 用户 ID 查询任务（供前端轮询任务进度时做权限校验）。 */
    Optional<AsyncJob> findByIdAndUserId(Long id, Long userId);

    /** 查询某用户指定类型的所有任务，按创建时间倒序（最新任务排第一）。 */
    List<AsyncJob> findByUserIdAndJobTypeOrderByCreatedAtDesc(Long userId, JobType type);

    /** 查询与某业务实体关联的所有任务（如某份简历触发的全部解析任务）。 */
    List<AsyncJob> findByRelatedEntityTypeAndRelatedEntityId(String entityType, Long entityId);
}
