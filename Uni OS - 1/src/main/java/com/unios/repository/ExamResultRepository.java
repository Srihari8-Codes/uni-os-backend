package com.unios.repository;

import com.unios.model.ExamResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExamResultRepository extends JpaRepository<ExamResult, Long> {
    Optional<ExamResult> findByApplicationId(Long applicationId);
    boolean existsByApplicationId(Long applicationId);

    long countByApplicationBatchId(Long batchId);
}
