package com.unios.service.agents.admissions;

import com.unios.model.Application;
import com.unios.repository.ApplicationRepository;
import com.unios.service.EmailService;
import com.unios.service.agents.framework.AgentWorkTask;
import com.unios.service.agents.framework.AsyncAgentQueue;
import com.unios.service.agents.framework.v5.tool.impl.DocumentAnalyzerTool;

import com.unios.service.orchestrator.InstitutionalOrchestrator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class EligibilityAgent {

    private final ApplicationRepository applicationRepository;
    private final AsyncAgentQueue asyncQueue;
    private final DocumentAnalyzerTool documentAnalyzerTool; // v5 tool
    private final EmailService emailService;
    private final InstitutionalOrchestrator orchestrator;
    private final com.unios.repository.StrategicLessonRepository strategicLessonRepository;

    public EligibilityAgent(ApplicationRepository applicationRepository,
                            AsyncAgentQueue asyncQueue,
                            DocumentAnalyzerTool documentAnalyzerTool,
                            EmailService emailService,
                            @Lazy InstitutionalOrchestrator orchestrator,
                            com.unios.repository.StrategicLessonRepository strategicLessonRepository) {
        this.applicationRepository = applicationRepository;
        this.asyncQueue = asyncQueue;
        this.documentAnalyzerTool = documentAnalyzerTool;
        this.emailService = emailService;
        this.orchestrator = orchestrator;
        this.strategicLessonRepository = strategicLessonRepository;
    }

    private void logActivity(String msg) {
        log.info(msg);
        orchestrator.getActivityLog().add("[AGENT] " + java.time.LocalDateTime.now() + ": " + msg);
    }

    @Transactional
    public void run(Long batchId) {
        // Fetch both SUBMITTED and REJECTED (for re-evaluation if OCR was missing)
        List<Application> appsToProcess = applicationRepository.findByBatchId(batchId).stream()
            .filter(a -> "SUBMITTED".equals(a.getStatus()) || ("REJECTED".equals(a.getStatus()) && a.getOcrAcademicScore() == null))
            .toList();

        processApplications(appsToProcess);
    }

    @Transactional
    public void checkEligibilityForSingle(Long applicationId) {
        Application app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new RuntimeException("Application not found: " + applicationId));
        processApplications(List.of(app));
    }

    private void processApplications(List<Application> appsToProcess) {
        if (appsToProcess.isEmpty()) return;
        com.unios.model.Batch batch = appsToProcess.get(0).getBatch();
        
        // Dynamic Cutoff from Batch configuration (default to 60% if null)
        double cutoffPercentage = (batch != null && batch.getMinAcademicCutoff() != null) ? batch.getMinAcademicCutoff() : 60.0;
        double cutoffScore = cutoffPercentage * 6.0;

        for (Application app : appsToProcess) {
            logActivity("Auditing candidate: " + app.getFullName() + " (Status: " + app.getStatus() + ") for batch: " + app.getBatch().getName());

            try {
                // 1. OCR Extraction (Board Mark Analysis)
                if (app.getOcrAcademicScore() == null && app.getFilePath() != null) {
                    logActivity("Analyzing Marksheet (OCR) for: " + app.getFullName());
                    try {
                        // Gather strategic context from past batches
                        List<com.unios.model.StrategicLesson> lessons = strategicLessonRepository.findRecentLessons();
                        String strategicContext = lessons.stream()
                            .limit(3)
                            .map(com.unios.model.StrategicLesson::getLesson)
                            .collect(java.util.stream.Collectors.joining("\n"));

                        documentAnalyzerTool.execute(Map.of(
                                "applicationId", app.getId(),
                                "filePath", app.getFilePath(),
                                "strategicContext", strategicContext // Feed lessons into AI prompt
                        ));
                        // RE-FETCH app after OCR update to get fresh state in current session
                        app = applicationRepository.findById(app.getId()).get();
                    } catch (Exception e) {
                        logActivity("OCR Analysis Failed for " + app.getFullName() + ": " + e.getMessage());
                        continue;
                    }
                }

                // --- Logic: Multi-source Mark Check ---
                Double ocrPercent = app.getOcrAcademicScore();
                Double schoolMarks = app.getSchoolMarks();
                
                double finalPercentage = 0.0;
                
                if (ocrPercent != null && ocrPercent > 0) {
                    finalPercentage = ocrPercent;
                    logActivity("Using OCR Percentage: " + finalPercentage + "% for " + app.getFullName());
                } else if (schoolMarks != null && schoolMarks > 0) {
                    // If it's a raw score (e.g. 431), convert to % if we know base is 600
                    finalPercentage = schoolMarks <= 100 ? schoolMarks : (schoolMarks / 6.0);
                    logActivity("No OCR percentage, falling back to manual marks: " + schoolMarks + " (Converted: " + String.format("%.2f", finalPercentage) + "%) for " + app.getFullName());
                } else {
                    logActivity("Candidate " + app.getFullName() + " has no valid marks. Skipping.");
                    continue; 
                }

                // 2. Eligibility Decision
                if (finalPercentage >= cutoffPercentage) {
                    logActivity("ELIGIBLE (" + String.format("%.2f", finalPercentage) + "% >= " + cutoffPercentage + "%) for candidate: " + app.getFullName());
                    app.setStatus("ELIGIBLE");
                    applicationRepository.save(app);
                    
                    sendExamInvitation(app, finalPercentage, cutoffPercentage, cutoffScore);
                } else {
                    logActivity("REJECTED (" + String.format("%.2f", finalPercentage) + "% < " + cutoffPercentage + "%) for candidate: " + app.getFullName());
                    app.setStatus("REJECTED");
                    applicationRepository.save(app);
                }
            } catch (Exception e) {
                logActivity("CRITICAL ERROR processing " + app.getFullName() + ": " + e.getMessage());
                log.error("Error in EligibilityAgent loop", e);
            }
        }
    }


    public void checkEligibility(Long batchId) {
        run(batchId);
    }

    private void sendExamInvitation(Application app, double marks, double cutoffPercent, double cutoffScore) {
        String examPassword = java.util.UUID.randomUUID().toString().substring(0, 8);
        app.setExamPassword(examPassword);
        app.setStatus("EXAM_SCHEDULED");
        applicationRepository.save(app);

        // Convert raw marks back to percentage for display if needed, but here we show both
        double actualPercent = marks <= 100 ? marks : (marks / 6.0);

        String subject = "Unios OS: Entrance Exam Invitation - " + app.getFullName();
        String text = String.format(
            "Dear %s,\n\n" +
            "Congratulations! Your 12th Board marks (%.1f%%) have been verified via OCR.\n\n" +
            "You have surpassed the Minimum Eligibility threshold of %.1f%% (%d marks) set for %s.\n\n" +
            "Portal Link: https://long-drinks-repair.loca.lt/entrance-exam\n" +
            "Email: %s\n" +
            "Access Key: %s\n\n" +
            "RULES:\n" +
            "1. Anti-Cheat: Tab-switching or window-blurring will result in INSTANT LOGOUT and FAILURE.\n" +
            "2. Access: You can use any device, but only one session is allowed.\n\n" +
            "Best Regards,\nInstitutional Orchestrator",
            app.getFullName(), actualPercent, cutoffPercent, (int)cutoffScore, app.getBatch().getName(), app.getEmail(), examPassword
        );

        emailService.sendEmail(app.getEmail(), subject, text);
        log.info("[EligibilityAgent] Dispatched Exam Invitation to: {}", app.getEmail());
    }
}
