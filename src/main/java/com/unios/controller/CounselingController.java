package com.unios.controller;

import com.unios.model.Application;
import com.unios.model.Batch;
import com.unios.model.ExamResult;
import com.unios.model.Role;
import com.unios.model.User;
import com.unios.repository.ApplicationRepository;
import com.unios.repository.BatchRepository;
import com.unios.repository.ExamResultRepository;
import com.unios.repository.UserRepository;
import com.unios.service.agents.admissions.EnrollmentAgent;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

@RestController
@RequestMapping("/api/counseling")
@CrossOrigin(origins = "http://localhost:5173")
public class CounselingController {

    private final ApplicationRepository applicationRepository;
    private final EnrollmentAgent enrollmentAgent;
    private final ExamResultRepository examResultRepository;
    private final BatchRepository batchRepository;
    private final UserRepository userRepository;

    public CounselingController(ApplicationRepository applicationRepository,
            EnrollmentAgent enrollmentAgent,
            ExamResultRepository examResultRepository,
            BatchRepository batchRepository,
            UserRepository userRepository) {
        this.applicationRepository = applicationRepository;
        this.enrollmentAgent = enrollmentAgent;
        this.examResultRepository = examResultRepository;
        this.batchRepository = batchRepository;
        this.userRepository = userRepository;
    }

    /** Counselor-accessible endpoint to list only batches belonging to their university. */
    @GetMapping("/batches")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_COUNSELOR')")
    public ResponseEntity<List<Batch>> getBatches(Principal principal) {
        User user = userRepository.findByEmail(principal.getName()).orElseThrow();
        if (user.getUniversity() == null) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        List<Batch> batches = batchRepository.findAll();
        List<Batch> filtered = batches.stream()
                .filter(b -> b.getUniversity() != null && b.getUniversity().getId().equals(user.getUniversity().getId()))
                .sorted((a, b) -> b.getId().compareTo(a.getId()))
                .toList();

        return ResponseEntity.ok(filtered);
    }

    /**
     * Returns EXAM_PASSED, WAITLISTED, and COMPLETED applications for a batch,
     * restricted to the user's university.
     */
    @GetMapping("/batch/{batchId}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_COUNSELOR')")
    public ResponseEntity<?> getCounselingPending(@PathVariable Long batchId, Principal principal) {
        User user = userRepository.findByEmail(principal.getName()).orElseThrow();
        Batch batch = batchRepository.findById(batchId).orElse(null);

        if (batch == null || user.getUniversity() == null || !batch.getUniversity().getId().equals(user.getUniversity().getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied to this batch"));
        }

        // Pending for counseling: passed exam, offered admission, or explicitly pending
        List<Application> pending = applicationRepository.findByBatchIdAndStatusIn(batchId, 
            List.of("EXAM_PASSED", "COUNSELING_PENDING", "ADMISSION_OFFERED"));
            
        List<Application> waitlisted = applicationRepository.findByBatchIdAndStatus(batchId, "WAITLISTED");
        
        // Finalized: enrolled or completed
        List<Application> enrolled = applicationRepository.findByBatchIdAndStatusIn(batchId, 
            List.of("COMPLETED", "ENROLLED"));

        return ResponseEntity.ok(Map.of(
                "batch", batch,
                "pending", enrich(pending),
                "waitlisted", enrich(waitlisted),
                "enrolled", enrich(enrolled)));
    }

    /**
     * Enriches each application with its exam score from the exam_results table.
     */
    private List<Map<String, Object>> enrich(List<Application> apps) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Application app : apps) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", app.getId());
            entry.put("fullName", app.getFullName());
            entry.put("email", app.getEmail());
            entry.put("status", app.getStatus());
            entry.put("batchId", app.getBatch() != null ? app.getBatch().getId() : null);

            // Look up exam score from exam_results
            Optional<ExamResult> examResult = examResultRepository.findByApplicationId(app.getId());
            entry.put("examScore", examResult.map(ExamResult::getScore).orElse(null));

            result.add(entry);
        }
        return result;
    }

    @PostMapping("/finalize/{applicationId}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_COUNSELOR')")
    public ResponseEntity<?> finalizeEnrollment(
            @PathVariable Long applicationId,
            @RequestBody Map<String, Object> payload,
            Principal principal) {

        User user = userRepository.findByEmail(principal.getName()).orElseThrow();
        Application app = applicationRepository.findById(applicationId).orElse(null);

        if (app == null || user.getUniversity() == null || app.getBatch() == null || 
            !app.getBatch().getUniversity().getId().equals(user.getUniversity().getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied to this application"));
        }

        String department = (String) payload.get("department");
        Double fees = Double.valueOf(payload.get("fees").toString());
        String notes = (String) payload.get("notes");
        String profilePhoto = (String) payload.get("profilePhoto");
        String parentName = (String) payload.get("parentName");
        String parentEmail = (String) payload.get("parentEmail");
        String parentPhone = (String) payload.get("parentPhone");

        try {
            Map<String, String> credentials = enrollmentAgent.enrollSingle(applicationId, department, fees, notes, profilePhoto, parentName, parentEmail, parentPhone);
            enrollmentAgent.runWaitlistTopUp(app.getBatch().getId());

            return ResponseEntity.ok(Map.of(
                    "message", "Enrollment finalized successfully",
                    "credentials", credentials));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/reject/{applicationId}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_COUNSELOR')")
    public ResponseEntity<?> rejectApplication(@PathVariable Long applicationId, Principal principal) {
        User user = userRepository.findByEmail(principal.getName()).orElseThrow();
        try {
            Application app = applicationRepository.findById(applicationId)
                    .orElseThrow(() -> new RuntimeException("Application not found"));
            
            if (user.getUniversity() == null || app.getBatch() == null || 
                !app.getBatch().getUniversity().getId().equals(user.getUniversity().getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied to this application"));
            }

            app.setStatus("REJECTED_BY_COUNSELOR");
            applicationRepository.save(app);

            // CRITICAL: Trigger waitlist top-up because a vacancy opened in the counseling pool!
            enrollmentAgent.runWaitlistTopUp(app.getBatch().getId());

            return ResponseEntity.ok(Map.of("message", "Application rejected. Waitlist top-up triggered."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
