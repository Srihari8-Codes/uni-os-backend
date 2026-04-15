package com.unios.service.admissions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unios.model.Application;
import com.unios.repository.ApplicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;

@Service
public class FeatureService {

    private final ApplicationRepository applicationRepository;
    private final ObjectMapper objectMapper;

    public FeatureService(ApplicationRepository applicationRepository) {
        this.applicationRepository = applicationRepository;
        this.objectMapper = new ObjectMapper();
    }

    @Transactional
    public void extractFeatures(Long applicationId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new RuntimeException("Application not found"));

        if (application.getExtractedMarks() == null || application.getExtractedMarks().isEmpty()) {
            return;
        }

        try {
            JsonNode rootNode = objectMapper.readTree(application.getExtractedMarks());
            
            Double marksPercentage = rootNode.has("percentage") ? rootNode.get("percentage").asDouble() : null;
            
            List<Double> subjectScores = new ArrayList<>();
            if (rootNode.has("subjects")) {
                JsonNode subjectsNode = rootNode.get("subjects");
                Iterator<String> fieldNames = subjectsNode.fieldNames();
                while (fieldNames.hasNext()) {
                    subjectScores.add(subjectsNode.get(fieldNames.next()).asDouble());
                }
            }

            if (!subjectScores.isEmpty()) {
                double mean = subjectScores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                double variance = subjectScores.stream()
                        .mapToDouble(score -> Math.pow(score - mean, 2))
                        .average().orElse(0.0);
                
                double stdDev = Math.sqrt(variance);
                // Consistency is inversely related to standard deviation (e.g. 100 - stdDev)
                double consistency = Math.max(0, 100 - stdDev);
                
                application.setVariance(variance);
                application.setConsistency(consistency);
                application.setMarks(marksPercentage != null ? marksPercentage : mean);
            }
            
            // Set entrance score if available (fallback to NEET or examResult)
            if (application.getEntranceScore() == null) {
                 if (application.getExamResult() != null) {
                     application.setEntranceScore(application.getExamResult().getScore());
                 } else if (application.getNeetScore() != null) {
                     application.setEntranceScore(application.getNeetScore());
                 } else {
                     application.setEntranceScore(0.0); // Default or unassigned
                 }
            }

            applicationRepository.save(application);

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("[FEATURE_SERVICE] Failed to extract features for Application " + applicationId + ": " + e.getMessage());
        }
    }
}
