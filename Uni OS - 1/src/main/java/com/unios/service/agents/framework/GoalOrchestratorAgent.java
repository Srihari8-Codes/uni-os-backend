package com.unios.service.agents.framework;

import com.unios.model.AgentTask;
import com.unios.model.Goal;
import com.unios.repository.AgentTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * GoalOrchestratorAgent — the autonomous driver of University OS goals.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GoalOrchestratorAgent {

    private final AgentExecutionEngine executionEngine;
    private final AgentTaskRepository taskRepository;
    private final GoalManager goalManager;

    public void runGoalCycle() {
        log.info("[GOAL ORCHESTRATOR] Starting goal cycle at {}", LocalDateTime.now());

        List<Goal> goals = goalManager.getGoalsForCurrentCycle();

        if (goals.isEmpty()) {
            log.info("[GOAL ORCHESTRATOR] No active goals to pursue this cycle.");
            return;
        }

        for (Goal goal : goals) {
            log.info("[GOAL ORCHESTRATOR] Pursuing goal '{}' [priority={}, urgency={}]",
                    goal.getName(), goal.getPriority(), goal.getUrgencyScore());
            triggerGoalLoop(goal);
        }
    }

    public void triggerManualCycle() {
        log.info("[GOAL ORCHESTRATOR] Manual cycle triggered.");
        runGoalCycle();
    }

    private void triggerGoalLoop(Goal goal) {
        String taskId = UUID.randomUUID().toString();

        AgentTask task = new AgentTask();
        task.setTaskId(taskId);
        task.setAgentName(goal.getAgentName());
        task.setEntityType("UNIVERSITY");
        task.setEntityId(1L);
        task.setGoal(goal.getGoalStatement());
        task.setStatus("RUNNING");
        task.setCreatedAt(LocalDateTime.now());
        taskRepository.save(task);

        goalManager.markGoalPursued(goal.getId());

        try {
            executionEngine.runLoop(
                    taskId,
                    goal.getAgentName(),
                    "UNIVERSITY",
                    goal.getId(), 
                    goal.getGoalStatement(),
                    new HashMap<>()
            );
            task.setStatus("COMPLETED");
            task.setCompletedAt(LocalDateTime.now());
            log.info("[GOAL ORCHESTRATOR] Goal '{}' loop completed.", goal.getName());
        } catch (Exception e) {
            log.error("[GOAL ORCHESTRATOR] Goal '{}' loop FAILED: {}", goal.getName(), e.getMessage());
            task.setStatus("FAILED");
            task.setErrorMessage(e.getMessage());
        } finally {
            taskRepository.save(task);
        }
    }
}
