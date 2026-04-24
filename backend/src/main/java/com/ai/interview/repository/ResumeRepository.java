package com.ai.interview.repository;

import com.ai.interview.entity.Resume;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ResumeRepository extends JpaRepository<Resume, Long> {

    List<Resume> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long userId);

    Optional<Resume> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);

    Optional<Resume> findByUserIdAndIsActiveTrueAndDeletedAtIsNull(Long userId);
}
