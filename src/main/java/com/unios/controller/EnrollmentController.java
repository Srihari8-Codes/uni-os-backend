package com.unios.controller;

import com.unios.service.agents.admissions.EnrollmentAgent;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/enrollments")
public class EnrollmentController {

    private final EnrollmentAgent enrollmentAgent;

    public EnrollmentController(EnrollmentAgent enrollmentAgent) {
        this.enrollmentAgent = enrollmentAgent;
    }

    /**
     * Bulk-friendly enrollment endpoint.
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Map<String, String>> enrollStudent(@RequestBody Map<String, Object> request) {
        Long appId = Long.valueOf(request.get("applicationId").toString());
        String department = (String) request.getOrDefault("department", "General");
        Double finalFees = Double.valueOf(request.getOrDefault("finalFees", 0.0).toString());
        String notes = (String) request.getOrDefault("counselorNotes", "Automated Enrollment");

        Map<String, String> result = enrollmentAgent.enrollSingle(appId, department, finalFees, notes);
        return ResponseEntity.ok(result);
    }

    /**
     * Trigger waitlist top-up manually for a specific batch.
     */
    @PostMapping("/batch/{batchId}/top-up")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<String> triggerWaitlistTopUp(@PathVariable Long batchId) {
        enrollmentAgent.runWaitlistTopUp(batchId);
        return ResponseEntity.ok("Waitlist top-up process initiated for batch " + batchId);
    }
}
