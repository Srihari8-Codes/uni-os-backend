package com.unios.controller;

import com.unios.model.Batch;
import com.unios.model.Application;
import com.unios.repository.BatchRepository;
import com.unios.repository.ApplicationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import org.springframework.context.ApplicationEventPublisher;
import com.unios.domain.events.BatchClosedEvent;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/batches")
public class BatchController {

    @Autowired
    private BatchRepository batchRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private com.unios.repository.UserRepository userRepository;

    @Autowired
    private com.unios.service.agents.admissions.ExamSchedulerAgent examSchedulerAgent;

    @Autowired
    private com.unios.service.agents.admissions.RankingAgent rankingAgent;

    @Autowired
    private com.unios.service.academics.CourseTemplateService courseTemplateService;

    @Autowired
    private com.unios.repository.ProgramRepository programRepository;

    @Autowired
    private com.unios.repository.BatchProgramRepository batchProgramRepository;

    @Autowired
    private com.unios.repository.AdmissionWeightsRepository admissionWeightsRepository;

    /**
     * Create a new batch (Step 1 initial save).
     * Sets status to DRAFT if not provided.
     * Integrates Adaptive Admissions Weight Inheritance.
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Batch> createBatch(@RequestBody Batch batch, java.security.Principal principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        com.unios.model.User user = userRepository.findByEmail(principal.getName()).orElseThrow();
        batch.setUniversity(user.getUniversity());

        if (batch.getStatus() == null || batch.getStatus().isEmpty()) {
            batch.setStatus("DRAFT");
        }
        if (batch.getStartYear() == null) {
            batch.setStartYear(java.time.Year.now().getValue());
        }
        if (batch.getName() == null || batch.getName().trim().isEmpty() || "Batch undefined".equalsIgnoreCase(batch.getName()) || "undefined".equalsIgnoreCase(batch.getName())) {
            batch.setName("Batch " + batch.getStartYear());
        }
        if (batch.getBatchCode() == null || batch.getBatchCode().isEmpty()) {
            batch.setBatchCode(generateBatchCode(batch));
        }
        System.out.println("[BATCH_DEBUG] CREATING NEW BATCH: Name=" + batch.getName() + ", Code=" + batch.getBatchCode());
        Batch savedBatch = batchRepository.save(batch);
        System.out.println("[BATCH_DEBUG] SAVED BATCH ID: " + savedBatch.getId() + " for University: " + user.getUniversity().getName());

        // Phase 5: Weight Inheritance Logic
        try {
            // Find all previous batches for this university, sort by ID descending (proxy for newest)
            List<Batch> previousBatches = batchRepository.findByUniversityId(user.getUniversity().getId());
            previousBatches.sort((b1, b2) -> b2.getId().compareTo(b1.getId()));
            
            com.unios.model.AdmissionWeights newWeights = new com.unios.model.AdmissionWeights();
            newWeights.setBatchId(savedBatch.getId());

            // Find the most recent batch that isn't this exact one
            Optional<Batch> lastBatch = previousBatches.stream()
                .filter(b -> !b.getId().equals(savedBatch.getId()))
                .findFirst();

            if (lastBatch.isPresent()) {
                admissionWeightsRepository.findByBatchId(lastBatch.get().getId()).ifPresent(pastWeights -> {
                    newWeights.setMarksWeight(pastWeights.getMarksWeight());
                    newWeights.setConsistencyWeight(pastWeights.getConsistencyWeight());
                    newWeights.setEntranceWeight(pastWeights.getEntranceWeight());
                    newWeights.setVariancePenalty(pastWeights.getVariancePenalty());
                    System.out.println("[ADAPTIVE ADMISSIONS] Inherited weights from Batch " + lastBatch.get().getId() + " to new Batch " + savedBatch.getId());
                });
            } // Else: keep defaults defined in AdmissionWeights entity
            
            admissionWeightsRepository.save(newWeights);

        } catch (Exception e) {
            System.err.println("Failed to inherit weights for new batch: " + e.getMessage());
        }

        return ResponseEntity.ok(savedBatch);
    }

    /**
     * Get active batches.
     */
    @GetMapping("/active")
    public ResponseEntity<List<Batch>> getActiveBatches(java.security.Principal principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        com.unios.model.User user = userRepository.findByEmail(principal.getName()).orElseThrow();
        if (user.getUniversity() == null) return ResponseEntity.ok(List.of());

        return ResponseEntity.ok(batchRepository.findByUniversityIdAndStatus(user.getUniversity().getId(), "ACTIVE"));
    }

    /**
     * Get all batches (for admin listing).
     */
    @GetMapping
    public ResponseEntity<List<Batch>> getAllBatches(java.security.Principal principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        com.unios.model.User user = userRepository.findByEmail(principal.getName()).orElseThrow();
        List<Batch> batches = batchRepository.findByUniversityId(user.getUniversity().getId());
        System.out.println("[BATCH_DEBUG] RETURNING " + batches.size() + " BATCHES FOR UNIVERSITY: " + user.getUniversity().getName());
        batches.forEach(b -> System.out.println("  - Batch ID: " + b.getId() + ", Name: " + b.getName() + ", status: " + b.getStatus()));
        return ResponseEntity.ok(batches);
    }

    /**
     * Get a single batch by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getBatch(@PathVariable Long id, java.security.Principal principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        com.unios.model.User user = userRepository.findByEmail(principal.getName()).orElseThrow();
        
        return batchRepository.findById(id)
                .map(batch -> {
                    if (batch.getUniversity() == null || !batch.getUniversity().getId().equals(user.getUniversity().getId())) {
                        return ResponseEntity.status(403).body("Unauthorized: Batch belongs to another university");
                    }
                    return ResponseEntity.ok(batch);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Save wizard draft — partial update of batch fields.
     * Frontend sends only the fields that changed in the current step.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> updateBatch(@PathVariable Long id, @RequestBody Batch updates, java.security.Principal principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        com.unios.model.User user = userRepository.findByEmail(principal.getName()).orElseThrow();

        return batchRepository.findById(id)
                .map(existing -> {
                    if (existing.getUniversity() == null || !existing.getUniversity().getId().equals(user.getUniversity().getId())) {
                        return ResponseEntity.status(403).body("Unauthorized: Batch belongs to another university");
                    }
                    // Step 1
                    if (updates.getName() != null) {
                        String newName = updates.getName();
                        if (newName.trim().isEmpty() || "Batch undefined".equalsIgnoreCase(newName) || "undefined".equalsIgnoreCase(newName)) {
                            existing.setName("Batch " + existing.getStartYear());
                        } else {
                            existing.setName(newName);
                        }
                    }
                    if (updates.getStartYear() != null)
                        existing.setStartYear(updates.getStartYear());
                    if (updates.getEndYear() != null)
                        existing.setEndYear(updates.getEndYear());
                    if (updates.getIntakeCapacity() != null)
                        existing.setIntakeCapacity(updates.getIntakeCapacity());
                    if (updates.getDuration() != null)
                        existing.setDuration(updates.getDuration());
                    if (updates.getBatchCode() != null)
                        existing.setBatchCode(updates.getBatchCode());
                    if (updates.getDescription() != null)
                        existing.setDescription(updates.getDescription());
                    if (updates.getDepartments() != null)
                        existing.setDepartments(updates.getDepartments());

                    // Step 2 & 3: Handle offeredPrograms
                    if (updates.getOfferedPrograms() != null) {
                        // Avoid clear() and re-add with same IDs to prevent "detached entity" errors.
                        // Sync the collection based on Program ID.
                        
                        // 1. Remove programs no longer offered
                        existing.getOfferedPrograms().removeIf(bp -> 
                            updates.getOfferedPrograms().stream()
                                .noneMatch(u -> u.getProgram() != null && 
                                               u.getProgram().getId() != null && 
                                               u.getProgram().getId().toString().equals(bp.getProgram().getId().toString()))
                        );

                        // 2. Update existing or add new
                        for (com.unios.model.BatchProgram u : updates.getOfferedPrograms()) {
                            if (u.getProgram() == null || u.getProgram().getId() == null) continue;
                            
                            final String targetProgId = u.getProgram().getId().toString();
                            com.unios.model.BatchProgram existingBp = existing.getOfferedPrograms().stream()
                                .filter(bp -> bp.getProgram().getId().toString().equals(targetProgId))
                                .findFirst()
                                .orElse(null);

                            if (existingBp != null) {
                                // Update dynamic fields
                                if (u.getSubjects() != null) existingBp.setSubjects(u.getSubjects());
                                if (u.getTotalCreditsRequired() != null) existingBp.setTotalCreditsRequired(u.getTotalCreditsRequired());
                            } else {
                                // Add new one
                                u.setBatch(existing);
                                existing.getOfferedPrograms().add(u);
                            }
                        }
                    }

                    // Step 3
                    if (updates.getSeatCapacity() != null)
                        existing.setSeatCapacity(updates.getSeatCapacity());
                    if (updates.getWaitlistCapacity() != null)
                        existing.setWaitlistCapacity(updates.getWaitlistCapacity());
                    if (updates.getMinAcademicCutoff() != null)
                        existing.setMinAcademicCutoff(updates.getMinAcademicCutoff());
                    if (updates.getEntranceExam() != null)
                        existing.setEntranceExam(updates.getEntranceExam());
                    if (updates.getExamWeightage() != null)
                        existing.setExamWeightage(updates.getExamWeightage());
                    if (updates.getDocumentRequirements() != null)
                        existing.setDocumentRequirements(updates.getDocumentRequirements());
                    if (updates.getApplicationDeadline() != null)
                        existing.setApplicationDeadline(updates.getApplicationDeadline());

                    // Step 4
                    if (updates.getTuitionFee() != null)
                        existing.setTuitionFee(updates.getTuitionFee());
                    if (updates.getRegistrationFee() != null)
                        existing.setRegistrationFee(updates.getRegistrationFee());
                    if (updates.getSemesterFee() != null)
                        existing.setSemesterFee(updates.getSemesterFee());
                    if (updates.getLateFee() != null)
                        existing.setLateFee(updates.getLateFee());
                    if (updates.getAttendanceShortage() != null)
                        existing.setAttendanceShortage(updates.getAttendanceShortage());
                    if (updates.getLibraryDues() != null)
                        existing.setLibraryDues(updates.getLibraryDues());
                    if (updates.getScholarships() != null)
                        existing.setScholarships(updates.getScholarships());

                    if (updates.getStatus() != null)
                        existing.setStatus(updates.getStatus());

                    return ResponseEntity.ok(batchRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Initialize a batch — changes status from DRAFT to CREATED.
     * This is the final step of the wizard.
     */
    @PostMapping("/{id}/initialize")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> initializeBatch(@PathVariable Long id, java.security.Principal principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        com.unios.model.User user = userRepository.findByEmail(principal.getName()).orElseThrow();

        return batchRepository.findById(id)
                .map(batch -> {
                    if (batch.getUniversity() == null || !batch.getUniversity().getId().equals(user.getUniversity().getId())) {
                        return ResponseEntity.status(403).build();
                    }
                    if (!"DRAFT".equals(batch.getStatus()) && !"CREATED".equals(batch.getStatus())) {
                        return ResponseEntity.badRequest()
                                .body("Batch cannot be (re)initialized in status: " + batch.getStatus());
                    }
                    batch.setStatus("CREATED");
                    return ResponseEntity.ok(batchRepository.save(batch));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Update batch status (existing endpoint).
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Batch> updateBatchStatus(@PathVariable Long id, @RequestBody Map<String, String> updates) {
        return batchRepository.findById(id)
                .map(batch -> {
                    if (updates.containsKey("status")) {
                        String newStatus = updates.get("status");
                        batch.setStatus(newStatus);
                        if ("ACTIVE".equals(newStatus)) {
                            System.out.println("[BATCH CONTROLLER] Admissions closed for Batch " + id
                                    + ". Triggering BatchClosedEvent.");
                            eventPublisher.publishEvent(new BatchClosedEvent(this, id));
                        }
                    }
                    return ResponseEntity.ok(batchRepository.save(batch));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Open Admissions — sets a deadline and changes status to ADMISSIONS_OPEN.
     */
    @PostMapping("/{id}/open-admissions")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> openAdmissions(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        return batchRepository.findById(id)
                .map(batch -> {
                    if (!"CREATED".equals(batch.getStatus())) {
                        return ResponseEntity.badRequest()
                                .body("Only CREATED batches can open admissions. Current status: " + batch.getStatus());
                    }
                    if (payload.containsKey("deadline")) {
                        batch.setApplicationDeadline(payload.get("deadline"));
                    }
                    batch.setStatus("ADMISSIONS_OPEN");
                    return ResponseEntity.ok(batchRepository.save(batch));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get public batch details for the admissions portal.
     * Accessible without authentication.
     */
    @GetMapping("/public/{batchCode}")
    public ResponseEntity<?> getPublicBatchDetails(@PathVariable String batchCode) {
        // Find batch by batchCode. Since we don't have a specific method in repository
        // yet, we can filter all or ideally add findByBatchCode to BatchRepository.
        // For simplicity here, we'll fetch all and filter since we know batch codes are
        // unique per active/open batch.
        return batchRepository.findAll().stream()
                .filter(b -> batchCode.equalsIgnoreCase(b.getBatchCode()) ||
                        batchCode.equalsIgnoreCase(b.getName()) ||
                        (b.getBatchCode() != null && batchCode.equalsIgnoreCase(b.getBatchCode().replaceAll("-", "")))
                        ||
                        batchCode.replaceAll("%20", " ").equalsIgnoreCase(b.getName()))
                .findFirst()
                .map(batch -> {
                    if (!"ADMISSIONS_OPEN".equals(batch.getStatus()) && !"ACTIVE".equals(batch.getStatus())) {
                        return ResponseEntity.badRequest().body("Admissions are not currently open for this batch.");
                    }
                    // Return public projection of batch (omit sensitive fields if any, here we just
                    // return the batch object directly)
                    return ResponseEntity.ok(batch);
                })
                .orElse(ResponseEntity.notFound().build());

    }

    /**
     * Delete a batch and all its associated applications permanently.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> deleteBatch(@PathVariable Long id, java.security.Principal principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        com.unios.model.User user = userRepository.findByEmail(principal.getName()).orElseThrow();

        return batchRepository.findById(id).map(batch -> {
            if (batch.getUniversity() == null || !batch.getUniversity().getId().equals(user.getUniversity().getId())) {
                return ResponseEntity.status(403).build();
            }
            // Fetch and delete all associated applications to prevent foreign key
            // constraint violations
            List<Application> applications = applicationRepository.findByBatchId(id);
            applicationRepository.deleteAll(applications);

            // Delete the batch itself
            batchRepository.delete(batch);
            return ResponseEntity.ok("Batch deleted successfully");
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * NUKE ALL - Clear all batches and applications permanently.
     */
    @DeleteMapping("/nuke-all")
    // @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> nukeAllBatches() {
        applicationRepository.deleteAll();
        batchRepository.deleteAll();
        return ResponseEntity.ok("All batches and applications cleared permanently.");
    }

    /**
     * Get course templates for a program.
     */
    @GetMapping("/templates")
    public ResponseEntity<List<com.unios.dto.CourseTemplate>> getCourseTemplates(@RequestParam String programName) {
        return ResponseEntity.ok(courseTemplateService.getTemplatesForProgram(programName));
    }

    /**
     * Get all available academic programs.
     */
    @GetMapping("/programs")
    public ResponseEntity<List<com.unios.model.Program>> getAvailablePrograms() {
        return ResponseEntity.ok(programRepository.findAll());
    }

    // --- AGENT INTERACTION ENDPOINTS ---

    @PostMapping("/{id}/agent/generate-exam-schedule")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> generateExamSchedule(@PathVariable Long id) {
        return batchRepository.findById(id).map(batch -> {
            try {
                com.unios.model.ExamSchedule schedule = examSchedulerAgent.generateSchedule(id, batch);
                return ResponseEntity.ok(schedule);
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
            }
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/agent/approve-exam-schedule")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> approveExamSchedule(@PathVariable Long id) {
        try {
            examSchedulerAgent.approveSchedule(id);
            return ResponseEntity.ok(Map.of("message", "Schedule approved and simulated emails sent."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/agent/process-exam-results")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> processExamResults(@PathVariable Long id) {
        try {
            rankingAgent.processResults(id);
            return ResponseEntity
                    .ok(Map.of("message", "Exam results processed. Counseling scheduled. simulated emails sent."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private String generateBatchCode(Batch batch) {
        String base = batch.getName() != null ? batch.getName() : "batch";
        String code = base.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("-$", "");
        
        // Ensure uniqueness
        String result = code;
        int counter = 1;

        while (true) {
            final String currentCode = result;
            boolean collision = batchRepository.findAll().stream().anyMatch(b -> 
                currentCode.equalsIgnoreCase(b.getBatchCode()) && 
                (batch.getId() == null || !batch.getId().equals(b.getId()))
            );
            
            if (!collision) break;
            result = code + "-" + counter++;
        }
        return result;
    }
}
