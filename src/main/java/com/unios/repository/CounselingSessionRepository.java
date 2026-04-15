package com.unios.repository;

import com.unios.model.CounselingSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface CounselingSessionRepository extends JpaRepository<CounselingSession, Long> {
    List<CounselingSession> findByBatchId(Long batchId);

    List<CounselingSession> findBySessionDate(LocalDate sessionDate);
}
