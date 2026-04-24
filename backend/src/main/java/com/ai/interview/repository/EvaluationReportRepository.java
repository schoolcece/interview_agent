package com.ai.interview.repository;

import com.ai.interview.entity.EvaluationReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EvaluationReportRepository extends JpaRepository<EvaluationReport, Long> {

    Optional<EvaluationReport> findBySessionId(Long sessionId);

    Page<EvaluationReport> findByUserIdOrderByGeneratedAtDesc(Long userId, Pageable pageable);
}
