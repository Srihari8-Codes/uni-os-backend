package com.unios.repository;

import com.unios.model.ExamSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExamScheduleRepository extends JpaRepository<ExamSchedule, Long> {
    Optional<ExamSchedule> findByBatchId(Long batchId);
}
