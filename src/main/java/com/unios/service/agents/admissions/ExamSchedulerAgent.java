package com.unios.service.agents.admissions;

import com.unios.model.Application;
import com.unios.model.EntranceExamSession;
import com.unios.optimizer.domain.ExamSession;
import com.unios.optimizer.domain.Room;
import com.unios.optimizer.service.AllocationSolverService;
import com.unios.repository.ApplicationRepository;
import com.unios.repository.EntranceExamSessionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ExamSchedulerAgent {

    private final ApplicationRepository applicationRepository;
    private final EntranceExamSessionRepository examSessionRepository;
    private final AllocationSolverService allocationSolverService;
    private final com.unios.repository.RoomRepository roomRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final com.unios.service.EmailService emailService;
    private final com.unios.service.documents.PdfGenerationService pdfGenerationService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private final com.unios.repository.ExamScheduleRepository scheduleRepository;

    @org.springframework.beans.factory.annotation.Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    public ExamSchedulerAgent(ApplicationRepository applicationRepository,
            EntranceExamSessionRepository examSessionRepository,
            AllocationSolverService allocationSolverService,
            ApplicationEventPublisher eventPublisher,
            com.unios.repository.RoomRepository roomRepository,
            com.unios.repository.ExamScheduleRepository scheduleRepository,
            com.unios.service.EmailService emailService,
            com.unios.service.documents.PdfGenerationService pdfGenerationService,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.applicationRepository = applicationRepository;
        this.examSessionRepository = examSessionRepository;
        this.allocationSolverService = allocationSolverService;
        this.eventPublisher = eventPublisher;
        this.roomRepository = roomRepository;
        this.scheduleRepository = scheduleRepository;
        this.emailService = emailService;
        this.pdfGenerationService = pdfGenerationService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public com.unios.model.ExamSchedule generateSchedule(Long batchId, com.unios.model.Batch batch) {
        List<Application> eligibleApps = applicationRepository.findByBatchIdAndStatus(batchId, "ELIGIBLE");
        System.out.println(">>> [ExamSchedulerAgent] Batch " + batchId + " - Found " + eligibleApps.size() + " ELIGIBLE apps.");
        if (eligibleApps.isEmpty())
            throw new RuntimeException("No eligible applicants found to schedule.");

        List<com.unios.model.Room> dbRooms = roomRepository.findAll();
        int totalCap = dbRooms.stream().mapToInt(com.unios.model.Room::getCapacity).sum();

        if (totalCap == 0) {
            throw new RuntimeException("CRITICAL ERROR: No room capacity available. Cannot schedule exams.");
        }

        // 1. Prepare Optimizer Domain Models
        List<com.unios.optimizer.domain.Room> solverRooms = dbRooms.stream()
                .map(r -> new com.unios.optimizer.domain.Room(r.getId().toString(), r.getName(), r.getCapacity()))
                .collect(Collectors.toList());

        List<com.unios.optimizer.domain.ExamSession> solverSessions = new ArrayList<>();
        
        // Add EXISTING occupants from ALL batches as PINNED sessions
        List<Application> globalOccupants = applicationRepository.findByStatusIn(java.util.List.of("EXAM_SCHEDULED", "EXAM_IN_PROGRESS"));

        for (Application occ : globalOccupants) {
            if (occ.getExamHall() != null && occ.getExamTimeSlot() != null) {
                com.unios.optimizer.domain.Room solverRoom = new com.unios.optimizer.domain.Room(
                    occ.getExamHall().getId().toString(), 
                    occ.getExamHall().getName(), 
                    occ.getExamHall().getCapacity()
                );
                solverSessions.add(new com.unios.optimizer.domain.ExamSession(
                        occ.getId() + 1000000, // Unique ID for solver
                        occ.getId(),
                        occ.getStudent() != null ? occ.getStudent().getId() : null,
                        "Entrance Exam (Existing)",
                        solverRoom,
                        occ.getExamTimeSlot(),
                        true // PINNED
                ));
            }
        }

        // Add NEW applicants for CURRENT batch as PLANNING entities
        for (Application app : eligibleApps) {
            solverSessions.add(new com.unios.optimizer.domain.ExamSession(
                    app.getId(), 
                    app.getId(),
                    app.getStudent() != null ? app.getStudent().getId() : null, 
                    "Entrance Exam"
            ));
        }

        // Dynamically generate enough time slots to accommodate all students
        List<LocalTime> examTimeSlots = new java.util.ArrayList<>();
        int currentCapacity = 0;
        LocalTime currentSlot = LocalTime.of(9, 0); // Start at 9:00 AM

        while (currentCapacity < eligibleApps.size() && examTimeSlots.size() < 100) {
            examTimeSlots.add(currentSlot);
            currentCapacity += totalCap;

            // Increment by 4 hours
            currentSlot = currentSlot.plusHours(4);
            
            // If past 6 PM, wrap to next day (simulated by adding 1 minute to make the LocalTime instance distinct for OptaPlanner)
            if (currentSlot.getHour() >= 18 || currentSlot.getHour() < 9) {
                currentSlot = LocalTime.of(9, currentSlot.getMinute() + 1);
            }
        }

        System.out.println("[ExamSchedulerAgent] Generated " + examTimeSlots.size() + " timeslots to handle " + eligibleApps.size() + " students.");

        // 2. Solve the Allocation Problem (Robust OptaPlanner Logic)
        List<com.unios.optimizer.domain.ExamSession> solvedSessions = allocationSolverService
                .solveExamAllocation(solverRooms, solverSessions, examTimeSlots);

        // 3. Convert results to JSON for storage (Maintaining existing DB structure)
        // 3. Convert results to JSON for storage using ObjectMapper (Reliable)
        List<Map<String, Object>> allocations = solvedSessions.stream()
                .filter(s -> s.getAssignedRoom() != null)
                .map(s -> {
                    Map<String, Object> map = new java.util.HashMap<>();
                    map.put("appId", s.getApplicationId());
                    map.put("roomId", s.getAssignedRoom().getId());
                    map.put("roomName", s.getAssignedRoom().getName());
                    map.put("timeSlot", s.getTimeSlot());
                    return map;
                })
                .collect(Collectors.toList());

        String allocationsJson;
        try {
            allocationsJson = objectMapper.writeValueAsString(allocations);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize allocations to JSON.", e);
        }

        com.unios.model.ExamSchedule schedule = scheduleRepository.findByBatchId(batchId)
                .orElse(new com.unios.model.ExamSchedule());
        schedule.setBatch(batch);
        schedule.setExamDate(LocalDateTime.now().plusDays(7));
        schedule.setTotalCapacity(totalCap);
        schedule.setHallAllocations(allocationsJson.toString());
        schedule.setStatus("GENERATED");

        return scheduleRepository.save(schedule);
    }

    @Transactional
    public void approveSchedule(Long batchId) {
        System.out.println(">>> [ExamSchedulerAgent] Approving schedule for batch " + batchId);
        com.unios.model.ExamSchedule schedule = scheduleRepository.findByBatchId(batchId)
                .orElseThrow(() -> new RuntimeException("No schedule found for this batch."));
        
        System.out.println(">>> [ExamSchedulerAgent] Current schedule status: " + schedule.getStatus());
        if (!"GENERATED".equals(schedule.getStatus())) {
            throw new RuntimeException("Schedule is not in GENERATED state.");
        }

        List<Application> eligibleApps = applicationRepository.findByBatchIdAndStatus(batchId, "ELIGIBLE");
        System.out.println(">>> [ExamSchedulerAgent] Found " + eligibleApps.size() + " students for hall ticket processing.");

        com.fasterxml.jackson.databind.JsonNode allocationsNode;
        try {
            allocationsNode = objectMapper.readTree(schedule.getHallAllocations());
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse hall allocations JSON.", e);
        }

        for (Application app : eligibleApps) {
            String assignedRoom = "Unassigned";
            String assignedTime = "TBD";
            com.unios.model.Room resolvedHallEntity = null;
            if (allocationsNode != null && allocationsNode.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode node : allocationsNode) {
                    if (node.has("appId") && node.get("appId").asLong() == app.getId()) {
                        assignedRoom = node.get("roomName").asText();
                        assignedTime = node.get("timeSlot").asText();
                        if (node.has("roomId")) {
                            resolvedHallEntity = roomRepository.findById(node.get("roomId").asLong()).orElse(null);
                        }
                        break;
                    }
                }
            }

            try {
                // Generate and save temporary exam password
                String examPass = java.util.UUID.randomUUID().toString().substring(0, 6).toUpperCase();
                app.setExamPassword(examPass);
                
                // Save global hall assignment for shared hall management
                if (resolvedHallEntity != null) {
                    app.setExamHall(resolvedHallEntity);
                }
                if (!"TBD".equals(assignedTime)) {
                    app.setExamTimeSlot(java.time.LocalTime.parse(assignedTime));
                }
                
                applicationRepository.save(app);

                // REAL PDF GENERATION AND EMAIL
                String subject = "UniOS Admissions: Your Entrance Exam Hall Ticket";
                String body = "Dear " + app.getFullName() + ",\n\n"
                        + "Your entrance exam has been successfully scheduled. Please find your hall ticket attached.\n\n"
                        + "--- ENTRANCE EXAM PORTAL LOGIN ---\n"
                        + "Portal URL: " + frontendUrl + "/exams/entrance-exam\n"
                        + "Application ID: APP-" + app.getId() + "\n"
                        + "Exam Password: " + examPass + "\n"
                        + "----------------------------------\n\n"
                        + "Exam Date: " + schedule.getExamDate() + "\n"
                        + "Exam Time: " + assignedTime + "\n"
                        + "Room: " + assignedRoom + "\n\n"
                        + "Best regards,\nUniversity Admissions Team";

                byte[] realPdf = pdfGenerationService.generateHallTicket(app, schedule, assignedRoom, assignedTime);
                emailService.sendEmailWithAttachment(app.getEmail(), subject, body, "HallTicket_" + app.getId() + ".pdf",
                        realPdf);
                
                app.setStatus("EXAM_SCHEDULED");
                applicationRepository.save(app);
            } catch (Exception e) {
                System.err.println("❌ Failed to process hall ticket/email for app " + app.getId() + ": " + e.getMessage());
                // Non-fatal, continue with others
            }
        }

        schedule.setStatus("APPROVED");
        scheduleRepository.save(schedule);

        // Tell orchestrator we're done scheduling
        eventPublisher.publishEvent(new com.unios.domain.events.ExamScheduledEvent(this, batchId));
    }
}
