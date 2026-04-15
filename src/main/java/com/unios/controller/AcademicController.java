package com.unios.controller;

import com.unios.model.SlotEnrollment;
import com.unios.model.StudentCredit;
import com.unios.model.SubjectOffering;
import com.unios.repository.SlotEnrollmentRepository;
import com.unios.repository.StudentCreditRepository;
import com.unios.repository.SubjectOfferingRepository;
import com.unios.repository.SemesterExamScheduleRepository;
import com.unios.service.agents.academics.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping
public class AcademicController {

    private final SubjectOfferingAgent subjectOfferingAgent;
    private final SlotEnrollmentAgent slotEnrollmentAgent;
    private final AttendanceAgent attendanceAgent;
    private final RiskMonitoringAgent riskMonitoringAgent;
    private final SemesterExamAgent semesterExamAgent;
    private final StudentCreditRepository studentCreditRepository;
    private final SlotEnrollmentRepository slotEnrollmentRepository;
    private final SubjectOfferingRepository subjectOfferingRepository;
    private final SemesterExamScheduleRepository semesterExamScheduleRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final com.unios.repository.QuestionBankRepository questionBankRepository;

    public AcademicController(SubjectOfferingAgent subjectOfferingAgent,
            SlotEnrollmentAgent slotEnrollmentAgent,
            AttendanceAgent attendanceAgent,
            RiskMonitoringAgent riskMonitoringAgent,
            SemesterExamAgent semesterExamAgent,
            StudentCreditRepository studentCreditRepository,
            SlotEnrollmentRepository slotEnrollmentRepository,
            SubjectOfferingRepository subjectOfferingRepository,
            SemesterExamScheduleRepository semesterExamScheduleRepository,
            ApplicationEventPublisher eventPublisher,
            com.unios.repository.QuestionBankRepository questionBankRepository) {
        this.subjectOfferingAgent = subjectOfferingAgent;
        this.slotEnrollmentAgent = slotEnrollmentAgent;
        this.attendanceAgent = attendanceAgent;
        this.riskMonitoringAgent = riskMonitoringAgent;
        this.semesterExamAgent = semesterExamAgent;
        this.studentCreditRepository = studentCreditRepository;
        this.slotEnrollmentRepository = slotEnrollmentRepository;
        this.subjectOfferingRepository = subjectOfferingRepository;
        this.semesterExamScheduleRepository = semesterExamScheduleRepository;
        this.eventPublisher = eventPublisher;
        this.questionBankRepository = questionBankRepository;
    }

    // ── Offerings ──────────────────────────────────────────────────────────────

    /**
     * GET /offerings — Returns all subject offerings.
     * Accessible to STUDENT, FACULTY and ADMIN (see SecurityConfig).
     * Used by: FacultyCourses.jsx (list) and StudentEnroll.jsx (select).
     */
    @GetMapping("/offerings")
    public ResponseEntity<List<SubjectOffering>> getOfferings() {
        return ResponseEntity.ok(subjectOfferingRepository.findAll());
    }

    /**
     * POST /offerings — Create a new subject offering (FACULTY or ADMIN only).
     */
    @PostMapping("/offerings")
    public ResponseEntity<SubjectOffering> createOffering(@RequestBody SubjectOffering request,
            java.security.Principal principal) {
        Long facultyId = request.getFaculty() != null ? request.getFaculty().getId() : null;
        Long batchId = request.getBatch() != null ? request.getBatch().getId() : null;

        SubjectOffering offering = subjectOfferingAgent.createOffering(
                request.getSubjectName(),
                request.getSlot(),
                request.getCapacity(),
                request.getCredits(),
                request.getPrerequisite(),
                facultyId,
                batchId,
                principal != null ? principal.getName() : null);
        return ResponseEntity.ok(offering);
    }

    /** GET /offerings/pending */
    @GetMapping("/offerings/pending")
    public ResponseEntity<List<SubjectOffering>> getPendingOfferings() {
        return ResponseEntity.ok(subjectOfferingRepository.findByStatus("PENDING_APPROVAL"));
    }

    /** POST /offerings/{id}/approve */
    @PostMapping("/offerings/{id}/approve")
    public ResponseEntity<String> approveOffering(@PathVariable Long id) {
        subjectOfferingAgent.approveOffering(id);
        return ResponseEntity.ok("Offering approved.");
    }

    /** POST /offerings/{id}/reject */
    @PostMapping("/offerings/{id}/reject")
    public ResponseEntity<String> rejectOffering(@PathVariable Long id) {
        subjectOfferingAgent.rejectOffering(id);
        return ResponseEntity.ok("Offering rejected.");
    }

    // ── Enrollment ─────────────────────────────────────────────────────────────

    /** POST /enroll?studentId=X&offeringId=Y */
    @PostMapping("/enroll")
    public ResponseEntity<String> enroll(@RequestParam Long studentId, @RequestParam Long offeringId) {
        slotEnrollmentAgent.enroll(studentId, offeringId);
        return ResponseEntity.ok("Enrolled successfully.");
    }

    // ── Attendance ─────────────────────────────────────────────────────────────

    /**
     * POST /attendance — Accepts a JSON body { enrollmentId, present }.
     * Previously used @RequestParam which caused 400 errors when the frontend
     * sent a JSON body.
     */
    @PostMapping("/attendance")
    public ResponseEntity<String> markAttendance(@RequestBody AttendanceRequest req) {
        attendanceAgent.markAttendance(req.getEnrollmentId(), req.isPresent());
        return ResponseEntity.ok("Attendance marked.");
    }

    @lombok.Data
    static class AttendanceRequest {
        private Long enrollmentId;
        private boolean present;
    }

    // ── Academic Progress ──────────────────────────────────────────────────────

    /** GET /academic/progress/{studentId} */
    @GetMapping("/academic/progress/{studentId}")
    public ResponseEntity<Map<String, Object>> getProgress(@PathVariable Long studentId) {
        Map<String, Object> response = new HashMap<>();

        StudentCredit credit = studentCreditRepository.findByStudentId(studentId).orElse(null);
        response.put("earnedCredits", credit != null ? credit.getEarnedCredits() : 0);

        List<SlotEnrollment> enrollments = slotEnrollmentRepository.findByStudentId(studentId);
        response.put("enrolledSubjects", enrollments);

        long passed = enrollments.stream().filter(e -> "PASSED".equals(e.getStatus())).count();
        response.put("passedSubjects", passed);

        long failed = enrollments.stream().filter(e -> "FAILED".equals(e.getStatus())).count();
        response.put("failedSubjects", failed);

        response.put("riskStatus", riskMonitoringAgent.evaluateRisk(studentId));

        return ResponseEntity.ok(response);
    }

    // ── Subject Completion ─────────────────────────────────────────────────────

    /**
     * POST /academics/complete — Body: { slotEnrollmentId, marks }
     */
    @PostMapping("/academics/complete")
    public ResponseEntity<String> completeSubject(@RequestBody CompletionRequest request) {
        eventPublisher.publishEvent(new com.unios.domain.events.AcademicCompletionEvent(this,
                request.getSlotEnrollmentId(), request.getMarks()));
        return ResponseEntity.ok("Academic completion recorded. Processing results.");
    }

    @lombok.Data
    static class CompletionRequest {
        private Long slotEnrollmentId;
        private int marks;
    }

    // ── Admin Exam Schedule Management ─────────────────────────────────────────

    /**
     * GET /offerings/exam-schedules/pending
     * Returns all exam schedules awaiting admin approval.
     */
    @GetMapping("/offerings/exam-schedules/pending")
    public ResponseEntity<?> getPendingExamSchedules() {
        var schedules = semesterExamScheduleRepository.findByStatus("PENDING_ADMIN");
        List<Map<String, Object>> result = new ArrayList<>();
        for (var schedule : schedules) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", schedule.getId());
            m.put("subjectName", schedule.getSubjectOffering().getSubjectName());
            m.put("offeringId", schedule.getSubjectOffering().getId());
            m.put("examDate", schedule.getExamDate());
            m.put("room", schedule.getRoom());
            m.put("status", schedule.getStatus());
            m.put("hallAllocations", schedule.getHallAllocations());
            result.add(m);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * POST /offerings/exam-schedules/{id}/approve
     * Admin approves exam schedule — students can now access the exam page.
     */
    @PostMapping("/offerings/exam-schedules/{id}/approve")
    public ResponseEntity<?> approveExamSchedule(@PathVariable Long id) {
        try {
            semesterExamAgent.approveExamSchedule(id);
            return ResponseEntity.ok(Map.of("message", "Exam schedule approved. Students can now access the exam."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /offerings/{id}/process-results
     * Admin triggers result processing based on real ExamSubmission scores.
     * Passed students receive credits. Failed students are reset (no-arrear).
     */
    @PostMapping("/offerings/{id}/process-results")
    public ResponseEntity<?> processResults(@PathVariable Long id) {
        try {
            semesterExamAgent.processExamResults(id);
            return ResponseEntity.ok(Map.of("message",
                    "Results processed. Credits awarded to passing students. Failed students reset for re-enrollment."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /offerings/exam-questions/upload
     * Upload questions via CSV.
     * Expected CSV format: SubjectName, QuestionText, OptionA, OptionB, OptionC, OptionD, CorrectOption
     */
    @PostMapping("/offerings/exam-questions/upload")
    public ResponseEntity<?> uploadQuestionsCsv(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        if (file.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));

        List<com.unios.model.QuestionBank> questions = new ArrayList<>();
        int successCount = 0;

        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(file.getInputStream()))) {
            String line;
            boolean isFirstLine = true;
            while ((line = br.readLine()) != null) {
                if (isFirstLine && (line.toLowerCase().contains("subject") || line.toLowerCase().contains("question"))) {
                    isFirstLine = false;
                    continue;
                }
                isFirstLine = false;

                String[] data = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
                if (data.length >= 7) {
                    com.unios.model.QuestionBank qb = new com.unios.model.QuestionBank();
                    qb.setSubjectName(data[0].replace("\"", "").trim());
                    qb.setQuestionText(data[1].replace("\"", "").trim());
                    qb.setOptionA(data[2].replace("\"", "").trim());
                    qb.setOptionB(data[3].replace("\"", "").trim());
                    qb.setOptionC(data[4].replace("\"", "").trim());
                    qb.setOptionD(data[5].replace("\"", "").trim());
                    qb.setCorrectOption(data[6].replace("\"", "").trim().toUpperCase());
                    questions.add(qb);
                    successCount++;
                }
            }
            if (!questions.isEmpty()) questionBankRepository.saveAll(questions);
            return ResponseEntity.ok(Map.of("message", "Successfully parsed and saved " + successCount + " questions."));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", "Error parsing CSV: " + e.getMessage()));
        }
    }
}
