package com.ai.interview.repository;

import com.ai.interview.entity.UserKnowledgeMastery;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserKnowledgeMasteryRepository extends JpaRepository<UserKnowledgeMastery, Long> {

    Optional<UserKnowledgeMastery> findByUserIdAndKnowledgePointId(Long userId, Long knowledgePointId);

    @Query("""
        SELECT m FROM UserKnowledgeMastery m
        WHERE m.userId = :userId
        ORDER BY m.masteryMean ASC
        """)
    List<UserKnowledgeMastery> findWeakestPoints(@Param("userId") Long userId, Pageable pageable);

    List<UserKnowledgeMastery> findByUserIdAndNextReviewAtBefore(Long userId, Instant before);
}
