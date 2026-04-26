package com.ai.interview.repository;

import com.ai.interview.entity.AsyncJob;
import com.ai.interview.entity.JobType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AsyncJobRepository extends JpaRepository<AsyncJob, Long> {

    Optional<AsyncJob> findByIdAndUserId(Long id, Long userId);

    List<AsyncJob> findByUserIdAndJobTypeOrderByCreatedAtDesc(Long userId, JobType type);

    List<AsyncJob> findByRelatedEntityTypeAndRelatedEntityId(String entityType, Long entityId);
}
