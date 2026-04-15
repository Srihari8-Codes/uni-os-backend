package com.unios.service.admissions;

import com.unios.model.AdmissionWeights;
import com.unios.model.Application;
import com.unios.repository.AdmissionWeightsRepository;
import com.unios.repository.ApplicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScoringService {

    private final AdmissionWeightsRepository admissionWeightsRepository;
    private final ApplicationRepository applicationRepository;

    public ScoringService(AdmissionWeightsRepository admissionWeightsRepository,
                          ApplicationRepository applicationRepository) {
        this.admissionWeightsRepository = admissionWeightsRepository;
        this.applicationRepository = applicationRepository;
    }

    @Transactional
    public Double calculateScore(Application application) {
        Long batchId = application.getBatch() != null ? application.getBatch().getId() : null;
        if (batchId == null) return 0.0;

        AdmissionWeights weights = admissionWeightsRepository.findByBatchId(batchId)
                .orElseGet(() -> {
                    AdmissionWeights defaultWeights = new AdmissionWeights();
                    defaultWeights.setBatchId(batchId);
                    return admissionWeightsRepository.save(defaultWeights);
                });

        double marks = application.getMarks() != null ? application.getMarks() : 0.0;
        double consistency = application.getConsistency() != null ? application.getConsistency() : 0.0;
        double entranceScore = application.getEntranceScore() != null ? application.getEntranceScore() : 0.0;
        
        // Variance usually acts as a penalty, so we multiply variance by variancePenalty.
        // Or if variance is high, we subtract it.
        double variance = application.getVariance() != null ? application.getVariance() : 0.0;
        
        // Normalization is assumed to be within 0-100 scale for each feature.
        double marksContribution = marks * weights.getMarksWeight();
        double consistencyContribution = consistency * weights.getConsistencyWeight();
        double entranceContribution = entranceScore * weights.getEntranceWeight();
        double varianceContribution = -(variance * weights.getVariancePenalty());

        double finalScore = marksContribution + consistencyContribution + entranceContribution + varianceContribution;

        // Generate explainable decision reason
        java.util.Map<String, Double> contributions = new java.util.HashMap<>();
        contributions.put("Academic Performance", marksContribution);
        contributions.put("Subject Consistency", consistencyContribution);
        contributions.put("Entrance Score", entranceContribution);
        
        String reason = contributions.entrySet().stream()
                .sorted(java.util.Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(2)
                .map(java.util.Map.Entry::getKey)
                .collect(java.util.stream.Collectors.joining(" and "));
        
        if (varianceContribution < -10) { // Significant penalty
             reason += " (penalized for high variance)";
        }

        // --- Calculate Confidence Score ---
        // consistency (higher = better), variance (lower = better), entrance alignment
        // Example: (consistency * 0.4) + ((100 - varianceNormalized) * 0.3) + (entranceScore * 0.3)
        // Ensure variance doesn't exceed 100 for normalization logic
        double varianceNormalized = Math.min(100.0, variance);
        double consistencyVal = Math.min(100.0, consistency);
        double entranceVal = Math.min(100.0, entranceScore);
        
        double confidence = (consistencyVal * 0.4) + ((100.0 - varianceNormalized) * 0.3) + (entranceVal * 0.3);
        double normalizedConfidence = Math.max(0.0, Math.min(100.0, confidence));

        application.setFinalScore(finalScore);
        application.setConfidenceScore(normalizedConfidence);
        application.setDecisionReason("Selected based on strong " + reason.toLowerCase());
        applicationRepository.save(application);

        return finalScore;
    }
}
