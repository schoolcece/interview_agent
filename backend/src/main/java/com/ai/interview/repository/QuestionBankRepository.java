package com.ai.interview.repository;

import com.ai.interview.entity.QuestionBank;
import com.ai.interview.entity.QuestionBankStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface QuestionBankRepository extends JpaRepository<QuestionBank, Long> {

    Optional<QuestionBank> findByIdAndDeletedAtIsNull(Long id);

    Page<QuestionBank> findByDomainAndStatusAndDeletedAtIsNull(
            String domain, QuestionBankStatus status, Pageable pageable);
}
