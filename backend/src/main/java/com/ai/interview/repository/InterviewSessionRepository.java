package com.ai.interview.repository;

import com.ai.interview.entity.InterviewSession;
import com.ai.interview.entity.SessionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InterviewSessionRepository extends JpaRepository<InterviewSession, Long> {

    Page<InterviewSession> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Optional<InterviewSession> findByIdAndUserId(Long id, Long userId);

    long countByUserIdAndStatus(Long userId, SessionStatus status);
}
