package com.unios.service.orchestrator;

import com.unios.service.agents.framework.AgentExecutionEngine;
import com.unios.service.llm.ReasoningEngineService;
import com.unios.repository.BatchRepository;
import com.unios.model.Batch;
import com.unios.service.agents.admissions.EnrollmentAgent;
import com.unios.service.agents.admissions.EligibilityAgent;
import com.unios.service.agents.admissions.RankingAgent;
import com.unios.repository.ExamResultRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class InstitutionalOrchestrator {

    private final AgentExecutionEngine executionEngine;
    private final ReasoningEngineService reasoningEngine;
    private final BatchRepository batchRepository;
    private final com.unios.repository.RoomRepository roomRepository;
    private final com.unios.repository.SlotEnrollmentRepository slotEnrollmentRepository;
    private final com.unios.repository.ApplicationRepository applicationRepository;
    private final EnrollmentAgent enrollmentAgent;
    private final EligibilityAgent eligibilityAgent;
    private final RankingAgent rankingAgent;
    private final ExamResultRepository examResultRepository;

    public static final List<String> ACTIVITY_LOG = new CopyOnWriteArrayList<>();

    public InstitutionalOrchestrator(AgentExecutionEngine executionEngine,
                                       ReasoningEngineService reasoningEngine,
                                       BatchRepository batchRepository,
                                       com.unios.repository.RoomRepository roomRepository,
                                       com.unios.repository.SlotEnrollmentRepository slotEnrollmentRepository,
                                       com.unios.repository.ApplicationRepository applicationRepository,
                                       EnrollmentAgent enrollmentAgent,
                                       EligibilityAgent eligibilityAgent,
                                       RankingAgent rankingAgent,
                                       ExamResultRepository examResultRepository) {
        this.executionEngine = executionEngine;
        this.reasoningEngine = reasoningEngine;
        this.batchRepository = batchRepository;
        this.roomRepository = roomRepository;
        this.slotEnrollmentRepository = slotEnrollmentRepository;
        this.applicationRepository = applicationRepository;
        this.enrollmentAgent = enrollmentAgent;
        this.eligibilityAgent = eligibilityAgent;
        this.rankingAgent = rankingAgent;
        this.examResultRepository = examResultRepository;
    }

    public void processGoal(String goal) {
        logActivity("New Institutional Goal: " + goal);
        long totalEnrolled = slotEnrollmentRepository.countByStatus("ENROLLED");
        int totalRoomCap = roomRepository.findAll().stream().mapToInt(com.unios.model.Room::getCapacity).sum();
        Map<String, Object> state = new HashMap<>();
        state.put("enrollmentCount", totalEnrolled);
        state.put("capacity", totalRoomCap);
        state.put("availablePhases", "ADMISSIONS, ACADEMICS, HR");
        executionEngine.runLoop("Orch-" + java.util.UUID.randomUUID().toString(), "UniversityOrchestrator", "InstitutionalGoal", 0L, goal, state);
    }

    private void logActivity(String message) {
        String logEntry = "[ORCHESTRATOR] " + java.time.LocalDateTime.now() + ": " + message;
        log.info(logEntry);
        ACTIVITY_LOG.add(logEntry);
    }

    /**
     * THE SYSTEM HEARTBEAT (100% Autonomous)
     * Runs every 30 seconds to drive the University towards its goals.
     */
    @Scheduled(fixedDelay = 30000)
    public void runAutonomousCycle() {
        List<Batch> activeBatches = batchRepository.findAll().stream()
                .filter(b -> !"DRAFT".equals(b.getStatus()))
                .collect(java.util.stream.Collectors.toList());
        
        for (Batch batch : activeBatches) {
            String status = batch.getStatus();
            Long batchId = batch.getId();

            // 1. Auto-Eligibility (Move SUBMITTED -> ELIGIBLE)
            long submittedCount = applicationRepository.countByBatchIdAndStatus(batchId, "SUBMITTED");
            if (submittedCount > 0 && ("ADMISSIONS_OPEN".equals(status) || "ACTIVE".equals(status))) {
                logActivity("Autonomous Screening: Processing " + submittedCount + " new applications for " + batch.getName());
                eligibilityAgent.checkEligibility(batchId);
            }

            // 2. Auto-Ranking (Smarter Trigger)
            long scheduledCount = applicationRepository.countByBatchIdAndStatus(batchId, "EXAM_SCHEDULED");
            if (scheduledCount > 0) {
                // Condition A: All students have completed the exam (Simulated by ExamResult existence)
                long completedCount = examResultRepository.countByApplicationBatchId(batchId);
                boolean allCompleted = (completedCount >= scheduledCount);
                
                // Condition B: Deadline passed (If deadline string is parseable)
                boolean deadlinePassed = false;
                if (batch.getApplicationDeadline() != null) {
                    try {
                        // Assuming ISO-8601 or common format
                        java.time.ZonedDateTime deadline = java.time.ZonedDateTime.parse(batch.getApplicationDeadline());
                        deadlinePassed = java.time.ZonedDateTime.now().isAfter(deadline);
                    } catch (Exception e) {
                        // Fallback or ignore if unparseable
                    }
                }

                if (allCompleted || deadlinePassed) {
                   logActivity("Autonomous Ranking: Auto-Triggering for " + batch.getName() + " (Condition: " + (allCompleted ? "All Completed" : "Deadline Passed") + ")");
                   rankingAgent.processResults(batchId);
                }
            }

            // 3. Auto-Waitlist Top-Up (Move WAITLISTED -> COUNSELING_PENDING)
            enrollmentAgent.runWaitlistTopUp(batchId);
        }
    }

    @Scheduled(cron = "0 0 * * * *") // Every hour
    public void performHealthCheck() {
        logActivity("Initiating System-Wide Health Check...");
        long totalEnrolled = slotEnrollmentRepository.countByStatus("ENROLLED");
        int totalRoomCap = roomRepository.findAll().stream().mapToInt(com.unios.model.Room::getCapacity).sum();
        if (totalEnrolled > totalRoomCap) {
            logActivity("[WARNING] Institutional Overload: " + totalEnrolled + "/" + totalRoomCap);
        }
        logActivity("Health Check Completed.");
    }

    public List<String> getActivityLog() {
        return ACTIVITY_LOG;
    }
}
