package com.unios.controller;

import com.unios.domain.events.ApplicationSubmittedEvent;
import com.unios.model.Application;
import com.unios.model.Batch;
import com.unios.model.ExamResult;
import com.unios.repository.ApplicationRepository;
import com.unios.repository.BatchRepository;
import com.unios.repository.ExamResultRepository;
import com.unios.service.agents.admissions.EligibilityAgent;
import com.unios.service.agents.admissions.EnrollmentAgent;
import com.unios.service.agents.admissions.ExamSchedulerAgent;
import com.unios.service.agents.admissions.RankingAgent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping
public class AdmissionsController {

    private final ApplicationRepository applicationRepository;
    private final BatchRepository batchRepository;
    private final EligibilityAgent eligibilityAgent;
    private final ExamSchedulerAgent examSchedulerAgent;
    private final RankingAgent rankingAgent;
    private final EnrollmentAgent enrollmentAgent;
    private final ApplicationEventPublisher eventPublisher;
    private final ExamResultRepository examResultRepository;
    private final com.unios.repository.ExamScheduleRepository scheduleRepository;
    private final com.unios.repository.RoomRepository roomRepository;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final com.unios.service.documents.PdfGenerationService pdfService;
    private final com.unios.service.event.UniversityEventPublisher universityEventPublisher;

    public AdmissionsController(ApplicationRepository applicationRepository,
                               BatchRepository batchRepository,
                               EligibilityAgent eligibilityAgent,
                               ExamSchedulerAgent examSchedulerAgent,
                               RankingAgent rankingAgent,
                               EnrollmentAgent enrollmentAgent,
                               ApplicationEventPublisher eventPublisher,
                               ExamResultRepository examResultRepository,
                               com.unios.repository.ExamScheduleRepository scheduleRepository,
                               com.unios.repository.RoomRepository roomRepository,
                               com.fasterxml.jackson.databind.ObjectMapper objectMapper,
                               com.unios.service.documents.PdfGenerationService pdfService,
                               com.unios.service.event.UniversityEventPublisher universityEventPublisher) {
        this.applicationRepository = applicationRepository;
        this.batchRepository = batchRepository;
        this.eligibilityAgent = eligibilityAgent;
        this.examSchedulerAgent = examSchedulerAgent;
        this.rankingAgent = rankingAgent;
        this.enrollmentAgent = enrollmentAgent;
        this.eventPublisher = eventPublisher;
        this.examResultRepository = examResultRepository;
        this.scheduleRepository = scheduleRepository;
        this.roomRepository = roomRepository;
        this.objectMapper = objectMapper;
        this.pdfService = pdfService;
        this.universityEventPublisher = universityEventPublisher;
    }

    @PostMapping("/applications")
    @Transactional
    public ResponseEntity<Application> createApplication(@RequestBody Application application) {
        if (application.getBatch() != null && application.getBatch().getId() != null) {
            Batch batch = batchRepository.findById(application.getBatch().getId())
                    .orElseThrow(() -> new RuntimeException("Batch not found"));
            application.setBatch(batch);
        }
        application.setStatus("SUBMITTED");
        Application saved = applicationRepository.save(application);
        eventPublisher.publishEvent(new ApplicationSubmittedEvent(this, saved));
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/applications/{id}/link-applicant")
    @Transactional
    public ResponseEntity<?> linkApplicant(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        Application app = applicationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Application not found"));
        Long userId = body.get("userId");
        if (userId != null) {
            com.unios.model.User user = new com.unios.model.User();
            user.setId(userId);
            app.setApplicantUser(user);
            applicationRepository.save(app);
        }
        return ResponseEntity.ok(Map.of("status", "linked"));
    }

    @GetMapping("/admissions/batch/{batchId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<Application>> getApplicationsForBatch(@PathVariable Long batchId) {
        List<Application> applications = applicationRepository.findByBatchId(batchId);
        return ResponseEntity.ok(applications);
    }

    @GetMapping("/applications/{id}/pdf")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> downloadApplicationPdf(@PathVariable Long id) {
        try {
            Application app = applicationRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Application not found"));
            byte[] pdf = pdfService.generateApplicationPdf(app);
            return ResponseEntity.ok()
                    .header("Content-Type", "application/pdf")
                    .header("Content-Disposition", "attachment; filename=Application_" + id + ".pdf")
                    .body(pdf);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/admissions/batches/{batchId}/occupancy")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getRoomOccupancy(@PathVariable Long batchId) {
        // Shared Hall Management: Query global Application status instead of batch-specific JSON
        List<com.unios.model.Room> allRooms = roomRepository.findAll();
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        
        for (com.unios.model.Room room : allRooms) {
            long scheduledCount = applicationRepository.countByStatusAndExamHallId("EXAM_SCHEDULED", room.getId());
            long inProgressCount = applicationRepository.countByStatusAndExamHallId("EXAM_IN_PROGRESS", room.getId());
            int totalOccupied = (int) (scheduledCount + inProgressCount);
            
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("roomName", room.getName());
            map.put("capacity", room.getCapacity());
            map.put("occupied", totalOccupied);
            result.add(map);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/admissions/applications/{appId}/finalize-counseling")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Transactional
    public ResponseEntity<Application> finalizeCounseling(@PathVariable Long appId, @RequestParam String action) {
        Application app = applicationRepository.findById(appId)
                .orElseThrow(() -> new RuntimeException("Application not found"));

        if ("ADMIT".equalsIgnoreCase(action)) {
            app.setStatus("ADMISSION_OFFERED");
            applicationRepository.save(app);
            // Trigger enrollment agent to create student portal
            enrollmentAgent.enrollSingle(appId); 
            // EnrollmentAgent usually moves status to COMPLETED (we'll update it to ENROLLED)
        } else if ("REJECT".equalsIgnoreCase(action)) {
            app.setStatus("REJECTED_AT_COUNSELING");
            applicationRepository.save(app);
        } else if ("WAITLIST".equalsIgnoreCase(action)) {
            app.setStatus("WAITLISTED");
            applicationRepository.save(app);
        }

        // --- [EVENT DRIVEN] Trigger Reactive Vacancy Check ---
        if (app.getBatch() != null) {
            long enrolled = applicationRepository.countByBatchIdAndStatus(app.getBatch().getId(), "ENROLLED")
                          + applicationRepository.countByBatchIdAndStatus(app.getBatch().getId(), "COMPLETED");
            int capacity = app.getBatch().getSeatCapacity() != null ? app.getBatch().getSeatCapacity() : 0;
            int vacancy = capacity - (int) enrolled;
            
            if (vacancy > 0) {
                universityEventPublisher.publishVacancyChange(app.getBatch().getId(), vacancy);
            }
        }

        return ResponseEntity.ok(app);
    }
}
