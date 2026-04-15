package com.unios.repository;

import com.unios.model.StudentOutcome;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StudentOutcomeRepository extends JpaRepository<StudentOutcome, Long> {
    Optional<StudentOutcome> findByStudentId(Long studentId);

    @Query("SELECT o FROM StudentOutcome o WHERE o.student.batch.id = :batchId")
    List<StudentOutcome> findByBatchId(@Param("batchId") Long batchId);
}
