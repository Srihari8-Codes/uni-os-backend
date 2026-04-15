package com.unios.service.admissions;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unios.domain.events.ApplicantEligibilityApprovedEvent;
import com.unios.domain.events.ApplicantEligibilityRejectedEvent;
import com.unios.dto.admissions.EligibilityResult;
import com.unios.model.Application;
import com.unios.model.Batch;
import com.unios.repository.ApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import com.unios.service.agents.framework.v5.tool.impl.DocumentAnalyzerTool;
import com.unios.service.EmailService;

import java.time.LocalDate;
import java.time.Period;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EligibilityEngineService {

    private final ApplicationRepository applicationRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final DocumentAnalyzerTool documentAnalyzerTool;
    private final EmailService emailService;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    /**
     * Evaluates a completed application against batch admission rules.
     */
    public EligibilityResult evaluate(Long applicationId) {
        Application app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new RuntimeException("Application not found"));
        
        Batch batch = app.getBatch();
        Map<String, Object> data = parseData(app.getApplicationData());
        
        Map<String, String> checks = new HashMap<>();
        boolean isEligible = true;

        // 1. OCR results are now handled immediately during upload in ConversationalAdmissionService

        // 2. Academic Eligibility Check
        Double minCutoff = (batch != null && batch.getMinAcademicCutoff() != null) ? batch.getMinAcademicCutoff() : 60.0;
        
        Double rawMarks = app.getSchoolMarks();
        Double actualPercentage = null;

        // Use ONLY Manual Entry (schoolMarks) as the Primary Source of Truth
        if (rawMarks != null && rawMarks > 0) {
            actualPercentage = rawMarks <= 100 ? rawMarks : (rawMarks / 6.0); // Assuming 600 base
        }

        if (actualPercentage == null) {
            isEligible = false;
            checks.put("academic", "FAILED - No valid marks provided or OCR verification incomplete.");
        } else if (actualPercentage < minCutoff) {
            isEligible = false;
            checks.put("academic", String.format("FAILED - Marks %.1f%% below cutoff %.1f%%", actualPercentage, minCutoff));
        } else {
            checks.put("academic", String.format("PASSED - Marks %.1f%% >= cutoff %.1f%%", actualPercentage, minCutoff));
        }

        // 3. Documentation Completeness
        if (app.getFilePath() == null) {
            isEligible = false;
            checks.put("documentation", "FAILED - Marksheet not detected.");
        } else {
            checks.put("documentation", "PASSED - Required documents uploaded.");
        }

        // --- Result Finalization ---
        String status = isEligible ? "ELIGIBLE" : "INELIGIBLE";
        String reason = isEligible ? "Candidate meets all admission criteria." : "One or more eligibility checks failed.";

        // Update persistence
        app.setStatus(status);
        applicationRepository.save(app);

        // --- Automate Entrance Exam Workflow Trigger ---
        if (isEligible) {
            log.info("[EligibilityEngine] Publishing Approval Event for Application ID: {}", applicationId);
            eventPublisher.publishEvent(new ApplicantEligibilityApprovedEvent(this, applicationId));
            sendExamInvitation(app, actualPercentage, minCutoff, minCutoff * 6.0);
        } else {
            log.info("[EligibilityEngine] Publishing Rejection Event for Application ID: {}", applicationId);
            eventPublisher.publishEvent(new ApplicantEligibilityRejectedEvent(this, applicationId));
        }

        log.info("[EligibilityEngine] Evaluated candidate {} (ID: {}): {}", app.getFullName(), applicationId, status);

        return EligibilityResult.builder()
                .applicationId(applicationId)
                .isEligible(isEligible)
                .status(status)
                .reason(reason)
                .checks(checks)
                .build();
    }

    private Map<String, Object> parseData(String json) {
        if (json == null || json.isEmpty()) return new HashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("Failed to parse application data for eligibility check", e);
            return new HashMap<>();
        }
    }

    private void sendExamInvitation(Application app, double marks, double cutoffPercent, double cutoffScore) {
        String examPassword = java.util.UUID.randomUUID().toString().substring(0, 8);
        app.setExamPassword(examPassword);
        app.setStatus("EXAM_SCHEDULED");
        applicationRepository.save(app);

        double actualPercent = marks <= 100 ? marks : (marks / 6.0);

        String subject = "Unios OS: Entrance Exam Invitation - " + app.getFullName();
        String text = String.format(
            "Dear %s,\n\n" +
            "Congratulations! Your 12th Board marks (%.1f%%) have been verified via OCR.\n\n" +
            "You have surpassed the Minimum Eligibility threshold of %.1f%% (%d marks) set for %s.\n\n" +
            "Portal Link: " + frontendUrl + "/entrance-exam\n" +
            "Email: %s\n" +
            "Access Key: %s\n\n" +
            "RULES:\n" +
            "1. Anti-Cheat: Tab-switching or window-blurring will result in INSTANT LOGOUT and FAILURE.\n" +
            "2. Access: You can use any device, but only one session is allowed.\n\n" +
            "Best Regards,\nInstitutional Orchestrator",
            app.getFullName(), actualPercent, cutoffPercent, (int)cutoffScore, app.getBatch().getName(), app.getEmail(), examPassword
        );

        emailService.sendEmail(app.getEmail(), subject, text);
        log.info("[EligibilityEngine] Dispatched Exam Invitation to: {}", app.getEmail());
    }
}
