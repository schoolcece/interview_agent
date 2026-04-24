package com.ai.interview.repository;

import com.ai.interview.entity.InterviewTurn;
import com.ai.interview.entity.TurnStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * ⚠️ 只读仓储：禁止 Service 层调用 save / delete。
 * <p>写入 interview_turns 由 Python Agent 独占。
 */
@Repository
public interface InterviewTurnRepository extends JpaRepository<InterviewTurn, Long> {

    List<InterviewTurn> findBySessionIdOrderByTurnNoAsc(Long sessionId);

    Optional<InterviewTurn> findBySessionIdAndTurnNo(Long sessionId, Integer turnNo);

    long countBySessionIdAndStatus(Long sessionId, TurnStatus status);
}
