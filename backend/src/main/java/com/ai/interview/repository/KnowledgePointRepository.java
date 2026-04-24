package com.ai.interview.repository;

import com.ai.interview.entity.KnowledgePoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface KnowledgePointRepository extends JpaRepository<KnowledgePoint, Long> {

    Optional<KnowledgePoint> findByCode(String code);

    List<KnowledgePoint> findByDomainAndActiveTrue(String domain);

    List<KnowledgePoint> findByCodeIn(Collection<String> codes);
}
