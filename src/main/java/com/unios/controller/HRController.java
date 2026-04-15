package com.unios.controller;

import com.unios.model.Candidate;
import com.unios.repository.CandidateRepository;
import com.unios.service.agents.hr.OnboardingAgent;
import com.unios.service.agents.hr.RecruitmentAgent;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/recruitment")
public class HRController {

    private final CandidateRepository candidateRepository;
    private final com.unios.repository.UserRepository userRepository;
    private final RecruitmentAgent recruitmentAgent;
    private final OnboardingAgent onboardingAgent;
    private final com.unios.repository.InterviewScheduleRepository interviewScheduleRepository;

    public HRController(CandidateRepository candidateRepository,
            com.unios.repository.UserRepository userRepository,
            RecruitmentAgent recruitmentAgent,
            OnboardingAgent onboardingAgent,
            com.unios.repository.InterviewScheduleRepository interviewScheduleRepository) {
        this.candidateRepository = candidateRepository;
        this.userRepository = userRepository;
        this.recruitmentAgent = recruitmentAgent;
        this.onboardingAgent = onboardingAgent;
        this.interviewScheduleRepository = interviewScheduleRepository;
    }

    @PostMapping
    public ResponseEntity<?> apply(@RequestBody Candidate candidate, java.security.Principal principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        com.unios.model.User user = userRepository.findByEmail(principal.getName()).orElseThrow();
        candidate.setUniversity(user.getUniversity());

        candidate.setStatus("APPLIED");
        Candidate saved = candidateRepository.save(candidate);

        // Trigger process
        recruitmentAgent.processCandidate(saved.getId());

        return ResponseEntity.ok(candidateRepository.findById(saved.getId()).orElse(saved));
    }

    @GetMapping("/applications")
    public ResponseEntity<?> listApplications(java.security.Principal principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        com.unios.model.User user = userRepository.findByEmail(principal.getName()).orElseThrow();
        return ResponseEntity.ok(candidateRepository.findByUniversityId(user.getUniversity().getId()));
    }

    @GetMapping("/schedules")
    public ResponseEntity<?> getSchedules(java.security.Principal principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        com.unios.model.User user = userRepository.findByEmail(principal.getName()).orElseThrow();
        
        java.util.List<com.unios.model.InterviewSchedule> filtered = interviewScheduleRepository.findAll().stream()
                .filter(s -> s.getCandidate() != null && s.getCandidate().getUniversity() != null && s.getCandidate().getUniversity().getId().equals(user.getUniversity().getId()))
                .toList();
        return ResponseEntity.ok(filtered);
    }

    @PostMapping("/{id}/decision")
    public ResponseEntity<String> decision(@PathVariable Long id, @RequestBody java.util.Map<String, Object> payload) {
        String status = (String) payload.get("status");
        Candidate candidate = candidateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Candidate not found"));

        if ("SELECTED".equals(status)) {
            candidate.setStatus("SELECTED");
            candidateRepository.save(candidate);

            String role = (String) payload.get("role");
            Object salaryObj = payload.get("salary");
            Double salary = 0.0;
            if (salaryObj instanceof Number) {
                salary = ((Number) salaryObj).doubleValue();
            } else if (salaryObj instanceof String) {
                salary = Double.parseDouble((String) salaryObj);
            }

            onboardingAgent.onboard(id, role, salary);
            return ResponseEntity.ok("Candidate selected and onboarded as faculty.");
        } else {
            candidate.setStatus("REJECTED");
            candidateRepository.save(candidate);
            return ResponseEntity.ok("Candidate marked as REJECTED.");
        }
    }

    @PostMapping("/{id}/onboard")
    public ResponseEntity<String> onboard(@PathVariable Long id,
            @RequestBody(required = false) java.util.Map<String, Object> payload) {
        String role = payload != null ? (String) payload.get("role") : "FACULTY";
        Double salary = payload != null && payload.get("salary") != null
                ? ((Number) payload.get("salary")).doubleValue()
                : 0.0;
        onboardingAgent.onboard(id, role, salary);
        return ResponseEntity.ok("Candidate status updated to ONBOARDED");
    }
}
