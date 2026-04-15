package com.unios.service.admissions;

import com.unios.domain.events.ApplicantEligibilityApprovedEvent;
import com.unios.domain.events.ApplicantEligibilityRejectedEvent;
import com.unios.model.Application;
import com.unios.repository.ApplicationRepository;
import com.unios.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EntranceExamWorkflowManager {

    private final ApplicationRepository applicationRepository;
    private final EmailService emailService;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @EventListener
    @Transactional
    public void handleEligibilityApproval(ApplicantEligibilityApprovedEvent event) {
        Long applicationId = event.getApplicationId();
        log.info("[EXAM-WORKFLOW] Triggering automated exam issuance for Application: {}", applicationId);

        Application app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new RuntimeException("Application not found in workflow context."));

        String examPassword = UUID.randomUUID().toString().substring(0, 8);
        app.setExamPassword(examPassword);
        app.setStatus("EXAM_SCHEDULED");

        applicationRepository.save(app);
        dispatchExamInvite(app, examPassword);
    }

    @EventListener
    @Transactional
    public void handleEligibilityRejection(ApplicantEligibilityRejectedEvent event) {
        Long applicationId = event.getApplicationId();
        log.info("[EXAM-WORKFLOW] Triggering rejection notification for Application: {}", applicationId);

        Application app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new RuntimeException("Application not found in workflow context."));

        app.setStatus("INELIGIBLE");
        applicationRepository.save(app);
        dispatchRejectionNotice(app);
    }

    private void dispatchExamInvite(Application app, String password) {
        String rawBatchName = app.getBatch() != null ? app.getBatch().getName() : "general";
        
        // Fix: Use URLEncoder to ensure spaces become %20 (matching the frontend browser URL)
        String encodedBatchName;
        try {
            encodedBatchName = java.net.URLEncoder.encode(rawBatchName, "UTF-8").replace("+", "%20");
        } catch (java.io.UnsupportedEncodingException e) {
            encodedBatchName = rawBatchName.replaceAll("\\s+", "%20");
        }

        // Exam attending URL from config
        String examUrl = frontendUrl + "/exams/entrance-exam";
        
        // Fix: Prioritize Manual Entry (schoolMarks) and calculate percentage correctly
        Double rawMarks = app.getSchoolMarks();
        Double marks = (rawMarks != null && rawMarks > 100) ? (rawMarks / 6.0) : rawMarks;
        if (marks == null) marks = app.getOcrAcademicScore();
        
        String marksDisplay = marks != null ? String.format("%.1f", marks) : "N/A";

        String subject = "Unios OS Admissions: Entrance Exam Eligibility & Hall Ticket";
        String body = String.format(
            "Dear %s,\n\n" +
            "Based on your marksheet, we have verified that you scored %s%%.\n" +
            "You are eligible for the entrance exam!\n\n" +
            "Please click below to attend the entrance exam:\n%s\n\n" +
            "Login ID (Email): %s\n" +
            "Login Password: %s\n\n" +
            "Last date to attend the entrance exam: 15 days from today\n\n" +
            "Best Regards,\nUniversity Admissions Board",
            app.getFullName(), marksDisplay, examUrl, app.getEmail(), password
        );

        try {
            emailService.sendEmail(app.getEmail(), subject, body);
            log.info("[EXAM-WORKFLOW] Notification dispatched to: {}", app.getEmail());
        } catch (Exception e) {
            log.error("[EXAM-WORKFLOW] CRITICAL: Failed to notify applicant {}", app.getEmail());
        }
    }

    private void dispatchRejectionNotice(Application app) {
        Double marks = app.getOcrAcademicScore() != null ? app.getOcrAcademicScore() : app.getSchoolMarks();
        String marksDisplay = marks != null ? String.format("%.1f", marks) : "N/A";

        String subject = "Unios OS Admissions: Application Update";
        String body = String.format(
            "Dear %s,\n\n" +
            "You've scored %s%% in your 12th board exam which is less than our minimum 60%% in board exam.\n" +
            "So we're sorry to inform you that you're ineligible for the entrance exam.\n\n" +
            "Best Regards,\nUniversity Admissions Board",
            app.getFullName(), marksDisplay
        );

        try {
            emailService.sendEmail(app.getEmail(), subject, body);
            log.info("[EXAM-WORKFLOW] Rejection notice dispatched to: {}", app.getEmail());
        } catch (Exception e) {
            log.error("[EXAM-WORKFLOW] CRITICAL: Failed to notify applicant {}", app.getEmail());
        }
    }
}
