package com.unios.service.agents.framework;

import com.unios.event.UniversityEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * EventDrivenOrchestrator — Replaces the cron-based heartbeat.
 *
 * This service listens for UniversityEvents and immediately triggers
 * the relevant agent cycles using the AgentExecutionEngine.
 *
 * Key features:
 *   - Reactive: Zero latency between event and action.
 *   - Priority-Aware: Could be extended to queue events by severity.
 *   - Contextual: Hand-offs event metadata directly to the agent.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EventDrivenOrchestrator {

    private final AgentExecutionEngine executionEngine;
    private final GoalManager goalManager;
    private final com.unios.repository.StudentRepository studentRepository;
    private final com.unios.service.communication.ParentCallService parentCallService;

    // Cooldown Map: StudentId-Subject -> LastCallTime
    private final Map<String, java.time.LocalDateTime> callCooldownMap = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int COOLDOWN_MINUTES = 0; // Set to 0 for immediate re-testing

    @Async // Run agent cycles in the background so the publisher isn't blocked
    @EventListener
    public void handleUniversityEvent(UniversityEvent event) {
        System.out.println("[DEBUG EVENT] Received event: " + event.getType() + " for metadata: " + event.getMetadata());
        log.info("[EVENT ORCHESTRATOR] Received event: {}. Activating agents...", event.getType());

        switch (event.getType()) {
            case ATTENDANCE_BREACH:
                triggerAttendanceResponse(event);
                break;
            case SINGLE_DAY_ABSENCE:
                triggerRandomAbsenceResponse(event);
                break;
            case SEAT_VACANCY_CHANGE:
                triggerAdmissionsResponse(event);
                break;
            case INTERVENTION_FAIL:
                triggerEscalationResponse(event);
                break;
            default:
                log.warn("[EVENT ORCHESTRATOR] Unhandled event type: {}", event.getType());
        }
    }

    private void triggerAttendanceResponse(UniversityEvent event) {
        Long studentId = (Long) event.getMetadata().get("studentId");
        String subject = (String) event.getMetadata().get("subject");
        
        log.info("[EVENT ORCHESTRATOR] Triggering AttendanceGuardian for student ID: {}", studentId);
        
        executionEngine.runLoop(
                "EVT-ATT-" + UUID.randomUUID().toString().substring(0, 8),
                "AttendanceGuardian",
                "STUDENT",
                studentId,
                "The student has tripped an attendance breach in " + subject + ". " +
                "Evaluate the risk, check if parent alerts are needed, and coordinate with the counselor.",
                event.getMetadata()
        );
    }

    private void triggerAdmissionsResponse(UniversityEvent event) {
        Long batchId = (Long) event.getMetadata().get("batchId");
        
        log.info("[EVENT ORCHESTRATOR] Triggering AdmissionsAgent for batch ID: {}", batchId);
        
        executionEngine.runLoop(
                "EVT-ADM-" + UUID.randomUUID().toString().substring(0, 8),
                "AdmissionsAgent",
                "BATCH",
                batchId,
                "A seat vacancy has been detected in this batch. " +
                "Evaluate waitlisted candidates and promote the best fit immediately.",
                event.getMetadata()
        );
    }

    private void triggerEscalationResponse(UniversityEvent event) {
        // Logic for handling failed interventions (e.g. notifying human admin)
        log.info("[EVENT ORCHESTRATOR] Handling intervention failure escalation.");
    }

    private void triggerRandomAbsenceResponse(UniversityEvent event) {
        try {
            Long studentId = Long.valueOf(event.getMetadata().get("studentId").toString());
            String subject = (String) event.getMetadata().get("subject");

            System.out.println("[DEBUG] triggerRandomAbsenceResponse started for studentId: " + studentId + ", subject: " + subject);

            // 1. Fetch Student for metadata
            com.unios.model.Student student = studentRepository.findById(studentId).orElse(null);
            if (student == null) {
                System.out.println("[DEBUG] Student NOT FOUND in database for ID: " + studentId);
                return;
            }
            System.out.println("[DEBUG] Student FOUND: " + student.getFullName() + ", Phone: " + student.getParentPhone());

            // 2. Cooldown Check (Synchronized to prevent race condition during simultaneous events)
            String cooldownKey = studentId + "-" + subject;
            synchronized(callCooldownMap) {
                java.time.LocalDateTime lastCall = callCooldownMap.get(cooldownKey);
                if (lastCall != null && lastCall.plusMinutes(COOLDOWN_MINUTES).isAfter(java.time.LocalDateTime.now())) {
                    System.out.println("[DEBUG] Cooldown active for " + cooldownKey + ". Skipping.");
                    return;
                }
                callCooldownMap.put(cooldownKey, java.time.LocalDateTime.now());
            }

            // 3. Initiate Call (Real-time)
            System.out.println("[DEBUG] Passing to ParentCallService.callParent (ID: " + studentId + ")...");
            parentCallService.callParent(studentId, subject);

            // 4. Continue with Agent Loop (Nudge/Reason Harvesting)
            System.out.println("[DEBUG] Triggering AttendanceGuardian agent loop...");
            executionEngine.runLoop(
                    "EVT-NUDGE-" + UUID.randomUUID().toString().substring(0, 8),
                    "AttendanceGuardian",
                    "STUDENT",
                    studentId,
                    "The student was marked absent for " + subject + " today. " +
                    "Initiate autonomous reason harvesting via call/SMS if needed.",
                    event.getMetadata()
            );
        } catch (Exception e) {
            System.err.println("[DEBUG ERROR] Exception in triggerRandomAbsenceResponse: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
