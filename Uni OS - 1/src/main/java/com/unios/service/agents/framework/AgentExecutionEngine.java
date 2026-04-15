package com.unios.service.agents.framework;

import com.unios.dto.DecisionResponse;
import com.unios.model.AgentAuditLog;
import com.unios.model.AgentTask;
import com.unios.repository.AgentAuditLogRepository;
import com.unios.repository.AgentTaskRepository;
import com.unios.service.llm.ReasoningEngineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * AgentExecutionEngine — upgraded with StateBoard integration.
 *
 * Every tool call now passes through the StateBoard:
 *   1. BEFORE acting → reads live university state into ToolContext
 *   2. AFTER acting  → publishes result to StateBoard for cross-agent visibility
 *
 * This breaks agent isolation and enables state-aware reasoning:
 * the LLM's planning prompt now includes the full university health snapshot,
 * so it can make globally-informed decisions.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AgentExecutionEngine {

    private final ReasoningEngineService reasoningEngine;
    private final List<AgentTool> tools;
    private final AgentMemoryService memoryService;
    private final AgentAuditLogRepository auditLogRepository;
    private final AgentTaskRepository taskRepository;
    private final com.unios.service.governance.DecisionValidator decisionValidator;
    private final UniversityStateBoard stateBoard; // ← NEW: shared intelligence layer
    private final AgentLearningService learningService; // ← NEW: implementation of learning loop

    public void runLoop(String taskId, String agentName, String entityType, Long entityId,
                        String goal, Map<String, Object> initialState) {

        log.info("[{}] Starting planning loop [Task: {}] for {} (ID: {})", agentName, taskId, entityType, entityId);

        long startTime = System.currentTimeMillis();
        boolean completed = false;
        int depth = 0;
        int maxDepth = 5;

        while (!completed && depth < maxDepth) {
            depth++;

            AgentTask currentTask = taskRepository.findById(taskId).orElse(null);

            // ── GOVERNANCE: CHECK FOR HUMAN OVERRIDE ──────────────────────────
            DecisionResponse plan = null;
            if (currentTask != null && currentTask.getOverrideAction() != null) {
                log.info("[{}] GOVERNANCE: Using Human Override decision: {}", agentName, currentTask.getOverrideAction());
                plan = new DecisionResponse();
                plan.setAction(currentTask.getOverrideAction());
                plan.setReasoning("HUMAN_OVERRIDE: " + currentTask.getOverrideReasoning());
                plan.setConfidence(1.0);
                currentTask.setOverrideAction(null);
                taskRepository.save(currentTask);
            } else {
                try {
                    // ── [STATE BOARD] READ: Inject live university state into planning prompt
                    UniversityStateSnapshot snap = stateBoard.getSnapshot();
                    String stateSummary = buildStateSummary(snap);
                    
                    // ── [LEARNING LOOP] READ: Inject historical tool performance data
                    String learningSummary = learningService.getLearningSummary();

                    String history = memoryService.getLongTermContext(agentName, entityType, entityId);
                    String currentContext = String.format(
                        "GOAL: %s\n" +
                        "UNIVERSITY STATE:\n%s\n" +
                        "EXPERIENCE SUMMARY:\n%s\n" + // ← NEW: Learning data injected here
                        "HISTORY: %s\n" +
                        "AVAILABLE TOOLS: %s\n" +
                        "CROSS-AGENT CONTEXT: %s\n" +
                        "INSTRUCTION: Think step-by-step. Use experience and current state to make the best decision.",
                        goal, stateSummary, learningSummary, history, getToolsDescriptions(),
                        buildCrossAgentContext(snap));

                    plan = reasoningEngine.decideStructured(
                        agentName, entityType, entityId,
                        "PLANNING",
                        currentContext
                    );
                } catch (Exception e) {
                    log.error("[{}] LLM Reasoning failed: {}. Critical stop.", agentName, e.getMessage());
                    return;
                }
            }

            // ── GOVERNANCE: GUARDRAILS ────────────────────────────────────────
            if (!decisionValidator.isValid(agentName, plan)) {
                log.error("[{}] GOVERNANCE: Guardrail violation. Proposed action '{}' is forbidden.",
                        agentName, plan.getAction());
                performDeterministicFallback(taskId, agentName, entityType, entityId, goal, initialState);
                return;
            }

            // ── GOVERNANCE: MANUAL APPROVAL INTERCEPTION ──────────────────────
            if (currentTask != null && Boolean.TRUE.equals(currentTask.getRequiresManualApproval())) {
                log.info("[{}] GOVERNANCE: Task requires manual approval. Pausing execution.", agentName);
                currentTask.setStatus("AWAITING_APPROVAL");
                taskRepository.save(currentTask);
                logAuditWithTrace(taskId, agentName, entityType, entityId, goal,
                        "PROPOSED: " + plan.getAction(), plan.getReasoning(), null,
                        "PAUSED for supervisor approval", plan.getConfidence(),
                        System.currentTimeMillis() - startTime, plan.getFullPrompt(), plan.getFullResponse());
                return;
            }

            log.info("[{}] PLAN (Step {}): {} (Confidence: {})", agentName, depth, plan.getAction(), plan.getConfidence());

            if ("FINALIZE".equalsIgnoreCase(plan.getAction()) || "COMPLETE".equalsIgnoreCase(plan.getAction())) {
                completed = true;
                logAuditWithTrace(taskId, agentName, entityType, entityId, goal, "FINALIZE",
                        plan.getReasoning(), null, "Goal achieved", plan.getConfidence(),
                        System.currentTimeMillis() - startTime, plan.getFullPrompt(), plan.getFullResponse());
                stateBoard.publishAgentResult(agentName, "FINALIZE", "Goal cycle completed."); // ← WRITE
                continue;
            }

            // ── ACT: Execute tool with StateBoard context ─────────────────────
            String toolResult = "";
            Optional<AgentTool> selectedTool = findTool(plan.getAction());
            if (selectedTool.isPresent()) {
                try {
                    log.info("[{}] ACT: Calling tool {} with parameters {}", agentName, plan.getAction(), plan.getParameters());

                    Map<String, Object> params = (plan.getParameters() != null) ? plan.getParameters() : initialState;

                    // ── [STATE BOARD] READ: Inject live state into ToolContext ──
                    ToolContext toolContext = ToolContext.builder()
                            .parameters(params)
                            .agentHistory(memoryService.getLongTermContext(agentName, entityType, entityId))
                            .systemState(stateBoard.getStateAsMap())   // ← live university state
                            .constraints(Map.of())
                            .build();

                    ToolResult result = selectedTool.get().executeWithContext(toolContext);
                    toolResult = result.getSummary() + " | Reasoning: " + result.getReasoning()
                               + " | Confidence: " + result.getConfidence();

                    memoryService.setShortTerm(agentName, "last_observation", result.getSummary());
                    memoryService.setShortTerm(agentName, "last_confidence", result.getConfidence());

                    // ── [LEARNING LOOP] WRITE: Record action outcome for future reference
                    learningService.recordOutcome(taskId, agentName, plan.getAction(), 
                            plan.getReasoning(), result, stateBoard.getSnapshot().getSystemHealthStatus());

                    // ── [STATE BOARD] WRITE: publish result for other agents ───
                    stateBoard.publishAgentResult(agentName, plan.getAction(), result.getSummary());

                    log.info("[{}] OBSERVE: {}", agentName, result.getSummary());
                } catch (Exception e) {
                    log.error("[{}] ERROR during act: {}", agentName, e.getMessage());
                    toolResult = "ERROR: " + e.getMessage();
                }
            } else {
                log.warn("[{}] ACT: No tool found matching action '{}'", agentName, plan.getAction());
                toolResult = "ERROR: Tool not found";
            }

            logAuditWithTrace(taskId, agentName, entityType, entityId, goal, plan.getAction(),
                    plan.getReasoning(), String.valueOf(plan.getParameters()), toolResult,
                    plan.getConfidence(), System.currentTimeMillis() - startTime,
                    plan.getFullPrompt(), plan.getFullResponse());

            // ── REFLECT ───────────────────────────────────────────────────────
            String reflectionPrompt = String.format(
                "GOAL: %s\n" +
                "ACTION TAKEN: %s\n" +
                "OBSERVATION: %s\n" +
                "UNIVERSITY STATE: %s\n" +
                "INSTRUCTION: Did we achieve the goal or do we need another step? " +
                "Consider the current university state. Respond with 'FINALIZE' if done, else 'CONTINUE'.",
                goal, plan.getAction(), toolResult, buildStateSummary(stateBoard.getSnapshot()));
            try {
                DecisionResponse reflection = reasoningEngine.decideStructured(
                        agentName, entityType, entityId, "REFLECTION", reflectionPrompt);
                if ("FINALIZE".equalsIgnoreCase(reflection.getAction())) {
                    completed = true;
                    log.info("[{}] REFLECTION: Goal met. Finalizing loop.", agentName);
                } else {
                    log.info("[{}] REFLECTION: Continuing. Reasoning: {}", agentName, reflection.getReasoning());
                }
            } catch (Exception e) {
                log.warn("[{}] Reflection parse failed. Defaulting to CONTINUE.", agentName);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // STATE SUMMARY BUILDERS
    // ─────────────────────────────────────────────────────────────────────────

    private String buildStateSummary(UniversityStateSnapshot snap) {
        return String.format(
            "- System Health: %s\n" +
            "- Seat Vacancy Rate: %.0f%% (%d batches have open seats)\n" +
            "- Waitlisted Applicants: %d | Admission Pressure: %.2f\n" +
            "- Average Attendance: %.1f%% | Trend: %s\n" +
            "- At-risk Students: %d (CRITICAL: %d)\n" +
            "- Parent Alerts Last 24h: %d",
            snap.getSystemHealthStatus(),
            snap.getOverallVacancyRate() * 100, snap.getBatchesWithVacancies(),
            snap.getTotalWaitlisted(), snap.getAdmissionPressureIndex(),
            snap.getAverageAttendancePct(), snap.getAttendanceTrend(),
            snap.getAtRiskStudentCount(), snap.getCriticalRiskStudentCount(),
            snap.getAlertsSentLast24h());
    }

    private String buildCrossAgentContext(UniversityStateSnapshot snap) {
        if (snap.isAdmissionsAttendanceConflictDetected()) {
            return "⚠ CONFLICT DETECTED: Admissions is filling seats while Attendance is DECLINING. " +
                   "Consider pausing promotions until attendance stabilizes. " +
                   "Last agent actions: " + snap.getLastAgentActions();
        }
        return "No cross-agent conflicts. Last agent actions: " + snap.getLastAgentActions();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // INTERNAL HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private void logAuditWithTrace(String taskId, String agentName, String entityType, Long entityId,
                                   String goal, String action, String reasoning, String input,
                                   String output, double confidence, long duration,
                                   String prompt, String response) {
        AgentAuditLog audit = AgentAuditLog.builder()
            .taskId(taskId).agentName(agentName).entityType(entityType).entityId(entityId)
            .goal(goal).action(action).reasoning(reasoning).toolInput(input).toolOutput(output)
            .confidence(confidence).executionTimeMs(duration).fullPrompt(prompt)
            .fullResponse(response).timestamp(LocalDateTime.now()).build();
        auditLogRepository.save(audit);
    }

    private void performDeterministicFallback(String taskId, String agentName, String entityType,
                                              Long entityId, String goal, Map<String, Object> initialState) {
        logAuditWithTrace(taskId, agentName, entityType, entityId, goal,
                "FALLBACK", "Guardrail violation", null, "Executed", 0.0, 0, "N/A", "N/A");
    }

    private Optional<AgentTool> findTool(String action) {
        return tools.stream().filter(t -> t.name().equalsIgnoreCase(action)).findFirst();
    }

    private String getToolsDescriptions() {
        StringBuilder sb = new StringBuilder();
        tools.forEach(t -> sb.append(t.name()).append(": ").append(t.description()).append("; "));
        return sb.toString();
    }
}
