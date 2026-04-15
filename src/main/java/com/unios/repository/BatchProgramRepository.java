package com.unios.repository;

import com.unios.model.BatchProgram;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BatchProgramRepository extends JpaRepository<BatchProgram, Long> {
    List<BatchProgram> findByBatchId(Long batchId);
    Optional<BatchProgram> findByBatchIdAndProgramId(Long batchId, Long programId);
}
