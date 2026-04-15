package com.unios.controller;

import com.unios.service.agents.graduation.CertificationAgent;
import com.unios.service.agents.graduation.GraduationEligibilityAgent;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map; // Import Map

@RestController
@RequestMapping("/graduation")
public class GraduationController {

    private final GraduationEligibilityAgent eligibilityAgent;
    private final CertificationAgent certificationAgent;
    private final com.unios.repository.GraduationCandidateRepository graduationCandidateRepository;

    public GraduationController(GraduationEligibilityAgent eligibilityAgent,
            CertificationAgent certificationAgent,
            com.unios.repository.GraduationCandidateRepository graduationCandidateRepository) {
        this.eligibilityAgent = eligibilityAgent;
        this.certificationAgent = certificationAgent;
        this.graduationCandidateRepository = graduationCandidateRepository;
    }

    @GetMapping("/candidates")
    public ResponseEntity<java.util.List<com.unios.model.GraduationCandidate>> getCandidates() {
        return ResponseEntity.ok(graduationCandidateRepository.findByStatus("IDENTIFIED"));
    }

    @PostMapping("/proceed")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<Map<String, Object>> proceedGraduation() {
        java.util.List<com.unios.model.GraduationCandidate> candidates = graduationCandidateRepository
                .findByStatus("IDENTIFIED");
        int count = 0;

        for (com.unios.model.GraduationCandidate candidate : candidates) {
            candidate.setStatus("ELIGIBLE");
            candidate.setProcessedAt(java.time.LocalDateTime.now());
            graduationCandidateRepository.save(candidate);

            // Trigger certification
            certificationAgent.issueCertificate(candidate.getStudent().getId());
            count++;
        }

        return ResponseEntity.ok(Map.of(
                "message", "Graduation process completed for " + count + " students.",
                "count", count));
    }

    @PostMapping("/run/{studentId}")
    public ResponseEntity<Map<String, String>> runGraduation(@PathVariable Long studentId) {
        boolean eligible = eligibilityAgent.checkEligibility(studentId);

        if (eligible) {
            certificationAgent.issueCertificate(studentId);
            return ResponseEntity.ok(Collections.singletonMap("status", "GRADUATED"));
        } else {
            return ResponseEntity.ok(Collections.singletonMap("status", "NOT_ELIGIBLE"));
        }
    }
}
