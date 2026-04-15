package com.unios.repository;

import com.unios.model.FeaturePerformance;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FeaturePerformanceRepository extends JpaRepository<FeaturePerformance, Long> {
    List<FeaturePerformance> findByBatchId(Long batchId);
    List<FeaturePerformance> findByFeatureNameOrderByBatchIdAsc(String featureName);
}
