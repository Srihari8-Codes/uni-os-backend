package com.unios.controller;

import com.unios.model.Goal;
import com.unios.service.agents.framework.GoalManager;
import com.unios.service.agents.framework.GoalOrchestratorAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * GoalController — Runtime CRUD for University Goals.
 *
 * Allows administrators to Create, Read, Update, and Abandon goals
 * without any redeployment. Goals take effect on the next orchestrator cycle.
 *
 * Endpoints secured to ADMIN role.
 */
@RestController
@RequestMapping("/api/goals")
@RequiredArgsConstructor
@Slf4j
public class GoalController {

    private final GoalManager goalManager;
    private final GoalOrchestratorAgent orchestratorAgent;

    /** GET all goals (with their current priority and urgency scores) */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Goal>> getAllGoals() {
        return ResponseEntity.ok(goalManager.getAllGoals());
    }

    /** GET a specific goal by ID */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Goal> getGoal(@PathVariable Long id) {
        return goalManager.getGoal(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** GET only the goals that would be selected in the next cycle (ranked) */
    @GetMapping("/active")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Goal>> getActiveGoals() {
        return ResponseEntity.ok(goalManager.getGoalsForCurrentCycle());
    }

    /** POST create a new goal at runtime */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Goal> createGoal(@RequestBody Goal goal) {
        log.info("[GOAL API] Creating new goal: {}", goal.getName());
        return ResponseEntity.ok(goalManager.createGoal(goal));
    }

    /** PUT update an existing goal (priority, statement, agent, category, etc.) */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Goal> updateGoal(
            @PathVariable Long id,
            @RequestBody Goal updates) {
        log.info("[GOAL API] Updating goal ID: {}", id);
        return ResponseEntity.ok(goalManager.updateGoal(id, updates));
    }

    /** PATCH abandon a goal at runtime with a reason */
    @PatchMapping("/{id}/abandon")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> abandonGoal(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.getOrDefault("reason", "Admin decision") : "Admin decision";
        goalManager.abandonGoal(id, reason);
        return ResponseEntity.ok().build();
    }

    /** PATCH mark a goal as completed */
    @PatchMapping("/{id}/complete")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> completeGoal(@PathVariable Long id) {
        goalManager.completeGoal(id);
        return ResponseEntity.ok().build();
    }

    /**
     * POST trigger an immediate orchestration cycle (manual override).
     * Useful for testing without waiting for the 15-minute heartbeat.
     */
    @PostMapping("/trigger-cycle")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> triggerCycle() {
        log.info("[GOAL API] Manual orchestration cycle triggered by admin.");
        orchestratorAgent.triggerManualCycle();
        return ResponseEntity.ok(Map.of("status", "Orchestration cycle started."));
    }
}
