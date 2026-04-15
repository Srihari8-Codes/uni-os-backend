package com.unios.repository;

import com.unios.model.ExamSubmission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ExamSubmissionRepository extends JpaRepository<ExamSubmission, Long> {
    Optional<ExamSubmission> findBySlotEnrollmentId(Long slotEnrollmentId);

    boolean existsBySlotEnrollmentId(Long slotEnrollmentId);
}
