package com.unios.repository;

import com.unios.model.WorkflowState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WorkflowStateRepository extends JpaRepository<WorkflowState, Long> {
    Optional<WorkflowState> findByBatchId(Long batchId);
}
