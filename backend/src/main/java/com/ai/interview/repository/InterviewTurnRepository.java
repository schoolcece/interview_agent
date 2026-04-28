package com.ai.interview.repository;

import com.ai.interview.entity.InterviewTurn;
import com.ai.interview.entity.TurnStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 面试轮次数据访问层。
 *
 * <p>⚠️ 只读仓储：Java Service 层禁止调用 {@code save / delete}。
 * {@code interview_turns} 表由 Python Agent 独占写入；Java 仅用于查询展示。
 */
@Repository
public interface InterviewTurnRepository extends JpaRepository<InterviewTurn, Long> {

    /** 获取某 session 下所有轮次，按轮次号升序（第 1 轮在前），供展示历史对话用。 */
    List<InterviewTurn> findBySessionIdOrderByTurnNoAsc(Long sessionId);

    /** 按 session + 轮次号定位单条记录（如需查询某一轮的评分详情）。 */
    Optional<InterviewTurn> findBySessionIdAndTurnNo(Long sessionId, Integer turnNo);

    /** 统计某 session 中处于特定状态的轮次数量（如已评分的轮次数）。 */
    long countBySessionIdAndStatus(Long sessionId, TurnStatus status);
}
