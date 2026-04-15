package com.unios.repository;

import com.unios.model.EntranceExamSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EntranceExamSessionRepository extends JpaRepository<EntranceExamSession, Long> {
    List<EntranceExamSession> findByBatchId(Long batchId);
}
