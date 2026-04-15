package com.unios.controller;

import com.unios.service.orchestrator.InstitutionalOrchestrator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/orchestrator")
public class OrchestratorController {

    private final InstitutionalOrchestrator orchestratorAgent;
    private final com.unios.service.policy.PolicyEngineService policyEngineService;
    private final com.unios.repository.WorkflowStateRepository workflowStateRepository;

    public OrchestratorController(InstitutionalOrchestrator orchestratorAgent,
            com.unios.service.policy.PolicyEngineService policyEngineService,
            com.unios.repository.WorkflowStateRepository workflowStateRepository) {
        this.orchestratorAgent = orchestratorAgent;
        this.policyEngineService = policyEngineService;
        this.workflowStateRepository = workflowStateRepository;
    }

    @GetMapping("/state")
    public ResponseEntity<List<String>> getOrchestratorState() {
        return ResponseEntity.ok(orchestratorAgent.getActivityLog());
    }

    @GetMapping("/workflow/{batchId}")
    public ResponseEntity<com.unios.model.WorkflowState> getBatchWorkflowState(
            @org.springframework.web.bind.annotation.PathVariable Long batchId) {
        return workflowStateRepository.findByBatchId(batchId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @org.springframework.web.bind.annotation.PostMapping("/semester-end")
    public ResponseEntity<String> triggerSemesterEnd() {
        policyEngineService.analyzeSemester();
        return ResponseEntity.ok("Semester analysis completed. System policies updated based on feedback.");
    }

    @org.springframework.web.bind.annotation.PostMapping("/health-check")
    public ResponseEntity<String> performHealthCheck() {
        orchestratorAgent.performHealthCheck();
        return ResponseEntity.ok("System-wide health check initiated. Check logs for details.");
    }
}
