package com.unios.repository;

import com.unios.model.AdmissionWeights;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AdmissionWeightsRepository extends JpaRepository<AdmissionWeights, Long> {
    Optional<AdmissionWeights> findByBatchId(Long batchId);
}
