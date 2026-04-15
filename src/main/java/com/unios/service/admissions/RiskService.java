package com.unios.service.admissions;

import com.unios.model.Student;
import com.unios.model.Application;
import com.unios.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RiskService {

    private final StudentRepository studentRepository;
    private final RiskLearningService riskLearningService;

    public RiskService(StudentRepository studentRepository, RiskLearningService riskLearningService) {
        this.studentRepository = studentRepository;
        this.riskLearningService = riskLearningService;
    }

    @Transactional
    public void evaluateRisk(Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        Application app = student.getApplication();
        if (app == null) {
            return;
        }

        double consistency = app.getConsistency() != null ? app.getConsistency() : 0.0;
        double variance = app.getVariance() != null ? app.getVariance() : 0.0;
        double entranceScore = app.getEntranceScore() != null ? app.getEntranceScore() : 0.0;
        
        // Fetch adaptive risk multiplier based on historical outcomes
        Long batchId = app.getBatch() != null ? app.getBatch().getId() : null;
        double riskMultiplier = 1.0;
        if (batchId != null) {
            riskMultiplier = riskLearningService.calculateRiskMultiplier(batchId);
        }

        double penaltyTargetVariance = 400 / riskMultiplier; // If riskMultiplier=2, high risk threshold for variance is 200 instead of 400
        double penaltyTargetConsistency = 60 * riskMultiplier;
        double penaltyTargetEntrance = 50 * riskMultiplier;

        double mediumVariance = 200 / riskMultiplier;
        double mediumConsistency = 75 * riskMultiplier;
        double mediumEntrance = 65 * riskMultiplier;

        String riskLevel = "LOW";
        
        if (consistency < penaltyTargetConsistency || variance > penaltyTargetVariance || entranceScore < penaltyTargetEntrance) {
            riskLevel = "HIGH";
        } else if (consistency < mediumConsistency || variance > mediumVariance || entranceScore < mediumEntrance) {
            riskLevel = "MEDIUM";
        }

        student.setRiskLevel(riskLevel);
        studentRepository.save(student);
    }
}
