package com.ai.interview.repository;

import com.ai.interview.entity.ErrorProblem;
import com.ai.interview.entity.ErrorStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ErrorProblemRepository extends JpaRepository<ErrorProblem, Long> {

    Optional<ErrorProblem> findByUserIdAndQuestionId(Long userId, Long questionId);

    Page<ErrorProblem> findByUserIdAndStatus(Long userId, ErrorStatus status, Pageable pageable);
}
