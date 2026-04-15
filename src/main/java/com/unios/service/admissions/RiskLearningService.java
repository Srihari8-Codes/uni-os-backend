package com.unios.service.admissions;

import com.unios.model.FeaturePerformance;
import com.unios.repository.FeaturePerformanceRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RiskLearningService {

    private final FeaturePerformanceRepository featurePerformanceRepository;

    public RiskLearningService(FeaturePerformanceRepository featurePerformanceRepository) {
        this.featurePerformanceRepository = featurePerformanceRepository;
    }

    /**
     * Calculates the risk penalty multiplier based on historical outcome data.
     * High multiplier = greater risk penalty. Base is 1.0.
     */
    public double calculateRiskMultiplier(Long currentBatchId) {
        // Fetch all historical performance data
        List<FeaturePerformance> allPerformance = featurePerformanceRepository.findAll();
        
        if (allPerformance.isEmpty()) {
            return 1.0; // Baseline if no learning data
        }

        // Group by batch to see trends
        Map<Long, List<FeaturePerformance>> byBatch = allPerformance.stream()
            .collect(Collectors.groupingBy(FeaturePerformance::getBatchId));

        double riskAdjustment = 1.0;

        // Example Logic: If historical variance has a high negative impact across batches,
        // we scale up the risk penalty.
        // Let's analyze "variance" feature across past batches
        List<FeaturePerformance> varianceStats = featurePerformanceRepository.findByFeatureNameOrderByBatchIdAsc("variance");
        
        if (!varianceStats.isEmpty()) {
            // Check if high variance consistently predicted low scores (negative avgOutcomeScore usually means the delta between High/Low variance was negative, meaning high variance = low score)
            // Note: ReflectionService calculates Delta as (HighGroup - LowGroup).
            // So if feature is "variance", HighGroup = high variance, LowGroup = low variance.
            // If delta < 0, high variance = worse score.
            long negativeImpactCount = varianceStats.stream().filter(fp -> fp.getAvgOutcomeScore() < 0).count();
            
            if (negativeImpactCount >= 1) {
                // If past batches showed risk features negatively impacted outcomes, increase risk base penalty by 10% per affected batch
                riskAdjustment += 0.1 * negativeImpactCount;
            }
        }
        
        return Math.min(riskAdjustment, 2.0); // Cap multiplier at 2.0x
    }
}
