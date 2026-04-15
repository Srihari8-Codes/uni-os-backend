package com.unios.controller;

import com.unios.model.*;
import com.unios.repository.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

@RestController
@RequestMapping("/api/student")
public class StudentPortalController {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final SlotEnrollmentRepository slotEnrollmentRepository;
    private final StudentCreditRepository studentCreditRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final com.unios.service.agents.academics.SlotEnrollmentAgent slotEnrollmentAgent;
    private final SubjectOfferingRepository subjectOfferingRepository;
    private final AttendanceRepository attendanceRepository;
    private final SemesterExamScheduleRepository semesterExamScheduleRepository;
    private final CourseExamRepository courseExamRepository;

    public StudentPortalController(UserRepository userRepository,
            StudentRepository studentRepository,
            SlotEnrollmentRepository slotEnrollmentRepository,
            StudentCreditRepository studentCreditRepository,
            EnrollmentRepository enrollmentRepository,
            com.unios.service.agents.academics.SlotEnrollmentAgent slotEnrollmentAgent,
            SubjectOfferingRepository subjectOfferingRepository,
            AttendanceRepository attendanceRepository,
            SemesterExamScheduleRepository semesterExamScheduleRepository,
            CourseExamRepository courseExamRepository) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.slotEnrollmentRepository = slotEnrollmentRepository;
        this.studentCreditRepository = studentCreditRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.slotEnrollmentAgent = slotEnrollmentAgent;
        this.subjectOfferingRepository = subjectOfferingRepository;
        this.attendanceRepository = attendanceRepository;
        this.semesterExamScheduleRepository = semesterExamScheduleRepository;
        this.courseExamRepository = courseExamRepository;
    }

    /**
     * GET /api/student/profile
     * Returns the logged-in student's full profile for the dashboard.
     */
    @GetMapping("/profile")
    @PreAuthorize("hasAuthority('ROLE_STUDENT')")
    public ResponseEntity<?> getProfile(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        User user = userRepository.findByEmail(principal.getName()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }

        Student student = studentRepository.findByUserId(user.getId()).orElse(null);
        if (student == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Student profile not found. Contact admissions."));
        }

        // Build profile response
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("studentId", student.getId());
        profile.put("fullName", student.getFullName());
        profile.put("email", user.getEmail());
        profile.put("department", student.getDepartment());
        profile.put("rollNumber", student.getRollNumber());
        profile.put("profilePhoto", student.getProfilePhoto());

        // Batch info
        if (student.getBatch() != null) {
            profile.put("batchName", student.getBatch().getName());
            profile.put("batchId", student.getBatch().getId());
        }

        // Enrolled subjects (slot enrollments)
        List<SlotEnrollment> enrollments = slotEnrollmentRepository.findByStudentId(student.getId());
        List<Map<String, Object>> subjects = new ArrayList<>();
        for (SlotEnrollment se : enrollments) {
            Map<String, Object> subj = new LinkedHashMap<>();
            subj.put("id", se.getId());
            subj.put("offeringId", se.getSubjectOffering() != null ? se.getSubjectOffering().getId() : null);
            subj.put("subjectName",
                    se.getSubjectOffering() != null ? se.getSubjectOffering().getSubjectName() : "Unknown");
            subj.put("credits", se.getSubjectOffering() != null ? se.getSubjectOffering().getCredits() : 0);
            subj.put("slot", se.getSubjectOffering() != null ? se.getSubjectOffering().getSlot() : "—");
            subj.put("status", se.getStatus());
            subj.put("examEligible", se.getExamEligible());
            subj.put("marks", se.getExamMarks());
            subj.put("creditsEarned", se.getCreditsEarned());

            // Attendance stats
            long total = attendanceRepository.countBySlotEnrollmentId(se.getId());
            long present = attendanceRepository.countBySlotEnrollmentIdAndPresentTrue(se.getId());
            subj.put("totalAttendance", total);
            subj.put("presentAttendance", present);
            subj.put("attendancePercentage", total == 0 ? 0 : Math.round(((double) present / total) * 1000.0) / 10.0);

            subjects.add(subj);
        }
        profile.put("enrolledSubjects", subjects);

        // Credits
        StudentCredit credit = studentCreditRepository.findByStudentId(student.getId()).orElse(null);
        profile.put("earnedCredits", credit != null ? credit.getEarnedCredits() : 0);
        profile.put("totalCreditsRequired", 140); // configurable later

        // Pending subjects = total enrolled - passed - failed
        long passed = enrollments.stream().filter(e -> "PASSED".equals(e.getStatus())).count();
        long failed = enrollments.stream().filter(e -> "FAILED".equals(e.getStatus())).count();
        profile.put("passedSubjects", passed);
        profile.put("failedSubjects", failed);
        profile.put("pendingSubjects", enrollments.size() - passed - failed);

        // Enrollment date
        List<Enrollment> admissionEnrollments = enrollmentRepository.findByStudentId(student.getId());
        if (!admissionEnrollments.isEmpty()) {
            profile.put("enrolledAt", admissionEnrollments.get(0).getEnrolledAt().toString());
        }

        // ... existing ...
        return ResponseEntity.ok(profile);
    }

    /**
     * GET /api/student/available-offerings
     * Returns all APPROVED offerings that the student is not already
     * enrolled/requested in.
     */
    @GetMapping("/available-offerings")
    @PreAuthorize("hasAuthority('ROLE_STUDENT')")
    public ResponseEntity<?> getAvailableOfferings(Principal principal) {
        User user = userRepository.findByEmail(principal.getName()).orElse(null);
        if (user == null)
            return ResponseEntity.status(404).body(List.of());

        Student student = studentRepository.findByUserId(user.getId()).orElse(null);
        if (student == null)
            return ResponseEntity.ok(List.of());

        // Get all APPROVED subjects
        List<SubjectOffering> allApproved = subjectOfferingRepository.findByStatus("APPROVED");

        // Get student's current enrollments/requests
        List<SlotEnrollment> studentEnrollments = slotEnrollmentRepository.findByStudentId(student.getId());
        Set<Long> enrolledOfferingIds = new HashSet<>();
        for (SlotEnrollment se : studentEnrollments) {
            enrolledOfferingIds.add(se.getSubjectOffering().getId());
        }

        // Filter out already enrolled/requested subjects
        List<Map<String, Object>> available = new ArrayList<>();
        for (SubjectOffering o : allApproved) {
            if (!enrolledOfferingIds.contains(o.getId())) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", o.getId());
                map.put("subjectName", o.getSubjectName());
                map.put("slot", o.getSlot());
                map.put("credits", o.getCredits());
                map.put("capacity", o.getCapacity());
                map.put("room", o.getRoom());
                map.put("facultyName", o.getFaculty() != null ? o.getFaculty().getFullName() : "TBD");
                available.add(map);
            }
        }

        return ResponseEntity.ok(available);
    }

    /**
     * POST /api/student/request-enrollment
     */
    @PostMapping("/request-enrollment")
    @PreAuthorize("hasAuthority('ROLE_STUDENT')")
    public ResponseEntity<?> requestEnrollment(Principal principal, @RequestBody Map<String, Long> payload) {
        User user = userRepository.findByEmail(principal.getName()).orElse(null);
        if (user == null)
            return ResponseEntity.status(404).body(Map.of("error", "Not authenticated"));

        Student student = studentRepository.findByUserId(user.getId()).orElse(null);
        if (student == null)
            return ResponseEntity.status(404).body(Map.of("error", "Student not found"));

        Long offeringId = payload.get("offeringId");
        if (offeringId == null)
            return ResponseEntity.badRequest().body(Map.of("error", "offeringId is required"));

        try {
            slotEnrollmentAgent.enroll(student.getId(), offeringId);
            return ResponseEntity.ok(Map.of("message", "Enrollment requested successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /api/student/hall-ticket/{offeringId}
     * Returns the student's hall ticket details for the specified exam.
     */
    @GetMapping("/hall-ticket/{offeringId}")
    @PreAuthorize("hasAuthority('ROLE_STUDENT')")
    public ResponseEntity<?> getHallTicket(@PathVariable Long offeringId, Principal principal) {
        User user = userRepository.findByEmail(principal.getName()).orElse(null);
        if (user == null)
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

        Student student = studentRepository.findByUserId(user.getId()).orElse(null);
        if (student == null)
            return ResponseEntity.status(404).body(Map.of("error", "Student not found"));

        SlotEnrollment enrollment = slotEnrollmentRepository
                .findByStudentIdAndSubjectOfferingId(student.getId(), offeringId).orElse(null);
        if (enrollment == null)
            return ResponseEntity.status(403).body(Map.of("error", "Not enrolled in this subject"));
        if (!Boolean.TRUE.equals(enrollment.getExamEligible()))
            return ResponseEntity.status(403).body(Map.of("error", "Not eligible for exam"));

        // Check new course_exams first
        Optional<CourseExam> courseExamOpt = courseExamRepository.findBySubjectOfferingId(offeringId);
        if (courseExamOpt.isPresent()) {
            CourseExam exam = courseExamOpt.get();
            Map<String, Object> ticket = new LinkedHashMap<>();
            ticket.put("studentName", student.getFullName());
            ticket.put("rollNumber", student.getRollNumber());
            ticket.put("subjectName", enrollment.getSubjectOffering().getSubjectName());
            ticket.put("offeringId", offeringId);
            ticket.put("examDate", exam.getStartTime().toString());
            ticket.put("room", exam.getExamHall().getName());
            ticket.put("seat", "A-" + (student.getId() % exam.getExamHall().getCapacity() + 1)); // Deterministic seat
            ticket.put("status", exam.getStatus());
            return ResponseEntity.ok(ticket);
        }

        // Fallback to legacy semester exam schedule
        SemesterExamSchedule schedule = semesterExamScheduleRepository.findBySubjectOfferingId(offeringId).orElse(null);
        if (schedule == null)
            return ResponseEntity.status(404).body(Map.of("error", "Exam not scheduled yet"));
        if (!"APPROVED".equals(schedule.getStatus()) && !"COMPLETED".equals(schedule.getStatus()))
            return ResponseEntity.status(403).body(Map.of("error", "Exam schedule not yet approved by admin"));

        // Find seat from hallAllocations JSON
        String seat = "TBD";
        String allocations = schedule.getHallAllocations();
        if (allocations != null && student.getRollNumber() != null) {
            String key = "\"" + student.getRollNumber() + "\"";
            int idx = allocations.indexOf(key);
            if (idx >= 0) {
                int seatStart = allocations.indexOf("Seat-", idx);
                if (seatStart >= 0) {
                    int seatEnd = allocations.indexOf("\"", seatStart);
                    seat = seatEnd > seatStart ? allocations.substring(seatStart, seatEnd) : "N/A";
                }
            }
        }

        Map<String, Object> ticket = new LinkedHashMap<>();
        ticket.put("studentName", student.getFullName());
        ticket.put("rollNumber", student.getRollNumber());
        ticket.put("subjectName", enrollment.getSubjectOffering().getSubjectName());
        ticket.put("offeringId", offeringId);
        ticket.put("examDate", schedule.getExamDate());
        ticket.put("room", schedule.getRoom());
        ticket.put("seat", seat);
        ticket.put("status", schedule.getStatus());
        return ResponseEntity.ok(ticket);
    }
}
