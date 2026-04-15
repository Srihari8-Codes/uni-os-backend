package com.unios.service.agents.admissions;

import com.unios.domain.events.BatchClosedEvent;
import com.unios.model.Batch;
import com.unios.repository.BatchRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import com.unios.domain.events.ApplicationSubmittedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Map;

@Component
@Slf4j
public class AdmissionsEventListener {

    private final EligibilityAgent eligibilityAgent;
    private final ExamSchedulerAgent examSchedulerAgent;
    private final RankingAgent rankingAgent;
    private final EnrollmentAgent enrollmentAgent;
    private final CounselingAgent counselingAgent;
    private final BatchRepository batchRepository;
    private final com.unios.repository.ApplicationRepository applicationRepository;
    private final ObjectMapper objectMapper;
    private final ReflectionAgent reflectionAgent;

    public AdmissionsEventListener(EligibilityAgent eligibilityAgent,
                                   ExamSchedulerAgent examSchedulerAgent,
                                   RankingAgent rankingAgent,
                                   EnrollmentAgent enrollmentAgent,
                                   CounselingAgent counselingAgent,
                                   BatchRepository batchRepository,
                                   com.unios.repository.ApplicationRepository applicationRepository,
                                   ObjectMapper objectMapper,
                                   ReflectionAgent reflectionAgent) {
        this.eligibilityAgent = eligibilityAgent;
        this.examSchedulerAgent = examSchedulerAgent;
        this.rankingAgent = rankingAgent;
        this.enrollmentAgent = enrollmentAgent;
        this.counselingAgent = counselingAgent;
        this.batchRepository = batchRepository;
        this.applicationRepository = applicationRepository;
        this.objectMapper = objectMapper;
        this.reflectionAgent = reflectionAgent;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Async
    public void handleApplicationSubmitted(ApplicationSubmittedEvent event) {
        com.unios.model.Application app = event.getApplication();
        log.info("[AdmissionsEventListener] Application {} submitted. Extracting documents for OCR...", app.getId());

        try {
            if (app.getApplicationData() != null) {
                Map<String, Object> data = objectMapper.readValue(
                    app.getApplicationData(), 
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}
                );
                String base64Content = (String) data.get("docTranscriptsContent");

                if (base64Content != null) {
                    // Remove data:application/pdf;base64, prefix if present
                    if (base64Content.contains(",")) {
                        base64Content = base64Content.split(",")[1];
                    }

                    byte[] pdfBytes = Base64.getDecoder().decode(base64Content);
                    String uploadDir = System.getProperty("user.dir") + "/uploads/transcripts";
                    File dir = new File(uploadDir);
                    if (!dir.exists()) dir.mkdirs();

                    String fileName = "transcript_" + app.getId() + "_" + System.currentTimeMillis() + ".pdf";
                    String filePath = uploadDir + "/" + fileName;
                    Files.write(Paths.get(filePath), pdfBytes);

                    app.setFilePath(filePath);
                    applicationRepository.save(app);
                    log.info("[AdmissionsEventListener] Document saved to: {}. Triggering Eligibility OCR...", filePath);
                } else {
                    log.warn("[AdmissionsEventListener] No transcript content found in application data for app: {}", app.getId());
                }
            }

            // Immediately run eligibility check for THIS APPLICATION ONLY (Stops App ID 3 spam)
            eligibilityAgent.checkEligibilityForSingle(app.getId());
            
        } catch (Exception e) {
            log.error("[AdmissionsEventListener] Error processing application {}: {}", app.getId(), e.getMessage(), e);
        }
    }

    @EventListener
    @Async
    public void handleBatchClosed(BatchClosedEvent event) {
        Long batchId = event.getBatchId();
        log.info("[AdmissionsEventListener] Batch {} closed. Starting automated admissions workflow phase 1-3.", batchId);

        try {
            log.info("[AdmissionsEventListener] Phase 1: Checking eligibility for batch {}. Fetches SUBMITTED apps.", batchId);
            eligibilityAgent.checkEligibility(batchId);
            log.info("[AdmissionsEventListener] Phase 1 COMPLETE for batch {}", batchId);
            
            Batch batch = batchRepository.findById(batchId)
                    .orElseThrow(() -> new RuntimeException("Batch not found: " + batchId));
            
            log.info("[AdmissionsEventListener] Phase 2: Generating exam schedule for batch {}. Fetches ELIGIBLE apps.", batchId);
            examSchedulerAgent.generateSchedule(batchId, batch);
            log.info("[AdmissionsEventListener] Phase 2 COMPLETE (Schedule GENERATED) for batch {}", batchId);
            
            log.info("[AdmissionsEventListener] Phase 3: Approving schedule & sending hall tickets for batch {}", batchId);
            examSchedulerAgent.approveSchedule(batchId);
            log.info("[AdmissionsEventListener] Phase 3 COMPLETE (Schedule APPROVED) for batch {}", batchId);
            
            // Phase 7: Post-Mortem Reflection & AI Strategy Generation
            log.info("[AdmissionsEventListener] Phase 7: Triggering Post-Mortem Analysis for batch {}", batchId);
            reflectionAgent.runPostMortem(batchId);

            log.info("[AdmissionsEventListener] Automated Admissions Workflow PHASES 1-7 SUCCESSFUL for batch {}", batchId);
        } catch (Exception e) {
            log.error("[AdmissionsEventListener] Error during automated admissions workflow for batch {}: {}", batchId, e.getMessage(), e);
        }
    }

    @EventListener
    @Async
    public void handleExamScheduled(com.unios.domain.events.ExamScheduledEvent event) {
        Long batchId = event.getBatchId();
        log.info("[AdmissionsEventListener] Phase 4: Exams Scheduled for batch {}. Simulating exam ingestion and pulling marks.", batchId);
        try {
            rankingAgent.processResults(batchId);
            log.info("[AdmissionsEventListener] Phase 5: Ranking evaluation tasks queued for batch {}.", batchId);
        } catch (Exception e) {
            log.error("[AdmissionsEventListener] Error during marks processing for batch {}: {}", batchId, e.getMessage(), e);
        }
    }

    @EventListener
    @Async
    public void handleApplicantPassed(com.unios.domain.events.ApplicantPassedEvent event) {
        Long appId = event.getApplicationId();
        log.info("[AdmissionsEventListener] Phase 6: Applicant {} passed entrance exams. Moving to COUNSELING_PENDING and triggering invitation.", appId);
        try {
            com.unios.model.Application app = applicationRepository.findById(appId).orElseThrow();
            app.setStatus("COUNSELING_PENDING");
            applicationRepository.save(app);

            // Trigger counseling invitation email dispatch for THIS specific applicant
            counselingAgent.prepareForSingle(appId);
            log.info("[AdmissionsEventListener] Phase 6 complete: Applicant {} successfully invited for counseling.", appId);
        } catch (Exception e) {
            log.error("[AdmissionsEventListener] Error during counseling processing for applicant {}: {}", appId, e.getMessage(), e);
        }
    }
}
