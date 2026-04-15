package com.unios.controller;

import com.unios.service.test.SimulationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/simulation")
public class SimulationController {

    private final SimulationService simulationService;

    public SimulationController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @PostMapping("/applicants/{batchId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<String> generateApplicants(@PathVariable Long batchId, @RequestParam(defaultValue = "1000") int count) {
        simulationService.generateApplicants(batchId, count);
        return ResponseEntity.ok("Successfully generated " + count + " simulation applicants.");
    }

    @PostMapping("/exam-scores/{batchId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<String> simulateExamScores(@PathVariable Long batchId) {
        simulationService.simulateExamScores(batchId);
        return ResponseEntity.ok("Successfully simulated exam scores.");
    }
}
