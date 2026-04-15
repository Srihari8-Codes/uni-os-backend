package com.unios.controller;

import com.unios.model.*;
import com.unios.repository.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;

@RestController
@RequestMapping("/api/faculty")
public class FacultyPortalController {

    private final UserRepository userRepository;
    private final FacultyRepository facultyRepository;
    private final SubjectOfferingRepository subjectOfferingRepository;
    private final SlotEnrollmentRepository slotEnrollmentRepository;
    private final com.unios.service.agents.academics.SlotEnrollmentAgent slotEnrollmentAgent;
    private final AttendanceRepository attendanceRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final com.unios.service.event.UniversityEventPublisher universityEventPublisher;
    private final com.unios.repository.AbsenceReasonRepository absenceReasonRepository;
    private final StudentRepository studentRepository;
    private final PasswordEncoder passwordEncoder;

    public FacultyPortalController(UserRepository userRepository,
            FacultyRepository facultyRepository,
            SubjectOfferingRepository subjectOfferingRepository,
            SlotEnrollmentRepository slotEnrollmentRepository,
            com.unios.service.agents.academics.SlotEnrollmentAgent slotEnrollmentAgent,
            AttendanceRepository attendanceRepository,
            ApplicationEventPublisher eventPublisher,
            com.unios.service.event.UniversityEventPublisher universityEventPublisher,
            com.unios.repository.AbsenceReasonRepository absenceReasonRepository,
            StudentRepository studentRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.facultyRepository = facultyRepository;
        this.subjectOfferingRepository = subjectOfferingRepository;
        this.slotEnrollmentRepository = slotEnrollmentRepository;
        this.slotEnrollmentAgent = slotEnrollmentAgent;
        this.attendanceRepository = attendanceRepository;
        this.eventPublisher = eventPublisher;
        this.universityEventPublisher = universityEventPublisher;
        this.absenceReasonRepository = absenceReasonRepository;
        this.studentRepository = studentRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * GET /api/faculty/profile
     */
    @GetMapping("/profile")
    @PreAuthorize("hasAnyAuthority('ROLE_FACULTY', 'ROLE_ADMIN')")
    public ResponseEntity<?> getProfile(Principal principal) {
        if (principal == null)
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

        User user = userRepository.findByEmail(principal.getName()).orElse(null);
        if (user == null)
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));

        Faculty faculty = facultyRepository.findByUserId(user.getId()).orElse(null);
        if (faculty == null)
            return ResponseEntity.status(404).body(Map.of("error", "Faculty profile not found"));

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("facultyId", faculty.getId());
        profile.put("fullName", faculty.getFullName());
        profile.put("email", user.getEmail());
        profile.put("department", faculty.getDepartment());

        // Count offerings
        List<SubjectOffering> offerings = subjectOfferingRepository.findByFacultyId(faculty.getId());
        long activeCount = offerings.stream().filter(o -> Boolean.TRUE.equals(o.getActive())).count();
        long draftCount = offerings.stream().filter(o -> !Boolean.TRUE.equals(o.getActive())).count();
        profile.put("activeSubjects", activeCount);
        profile.put("draftSubjects", draftCount);
        profile.put("totalStudents", 0); // will be computed when slot enrollments are wired

        // Compute total students across all active offerings
        long totalStudents = 0;
        for (SubjectOffering o : offerings) {
            if (Boolean.TRUE.equals(o.getActive())) {
                totalStudents += slotEnrollmentRepository.countBySubjectOfferingId(o.getId());
            }
        }
        profile.put("totalStudents", totalStudents);

        return ResponseEntity.ok(profile);
    }

    /**
     * GET /api/faculty/my-offerings
     */
    @GetMapping("/my-offerings")
    @PreAuthorize("hasAuthority('ROLE_FACULTY')")
    public ResponseEntity<?> getMyOfferings(Principal principal) {
        User user = userRepository.findByEmail(principal.getName()).orElse(null);
        if (user == null)
            return ResponseEntity.status(404).body(List.of());

        Faculty faculty = facultyRepository.findByUserId(user.getId()).orElse(null);
        if (faculty == null)
            return ResponseEntity.ok(List.of());

        List<SubjectOffering> offerings = subjectOfferingRepository.findByFacultyId(faculty.getId());
        List<Map<String, Object>> result = new ArrayList<>();

        for (SubjectOffering o : offerings) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", o.getId());
            entry.put("subjectName", o.getSubjectName());
            entry.put("slot", o.getSlot());
            entry.put("credits", o.getCredits());
            entry.put("capacity", o.getCapacity());
            entry.put("active", o.getActive());
            entry.put("room", o.getRoom());
            entry.put("prerequisite", o.getPrerequisite());
            entry.put("enrolledCount", slotEnrollmentRepository.countBySubjectOfferingId(o.getId()));
            if (o.getBatch() != null) {
                entry.put("batchName", o.getBatch().getName());
                entry.put("batchId", o.getBatch().getId());
            }
            result.add(entry);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/faculty/enrollment-requests
     * Returns all REQUESTED enrollments for this faculty's subjects.
     */
    @GetMapping("/enrollment-requests")
    @PreAuthorize("hasAuthority('ROLE_FACULTY')")
    public ResponseEntity<?> getEnrollmentRequests(Principal principal) {
        User user = userRepository.findByEmail(principal.getName()).orElse(null);
        if (user == null)
            return ResponseEntity.status(404).body(List.of());

        Faculty faculty = facultyRepository.findByUserId(user.getId()).orElse(null);
        if (faculty == null)
            return ResponseEntity.ok(List.of());

        List<SubjectOffering> myOfferings = subjectOfferingRepository.findByFacultyId(faculty.getId());
        List<Map<String, Object>> requests = new ArrayList<>();

        for (SubjectOffering o : myOfferings) {
            List<SlotEnrollment> pending = slotEnrollmentRepository.findBySubjectOfferingIdAndStatus(o.getId(),
                    "REQUESTED");
            for (SlotEnrollment se : pending) {
                Map<String, Object> req = new LinkedHashMap<>();
                req.put("id", se.getId());
                req.put("studentName", se.getStudent().getFullName());
                req.put("studentId", se.getStudent().getId());
                req.put("rollNumber", se.getStudent().getRollNumber());
                req.put("subjectName", o.getSubjectName());
                req.put("offeringId", o.getId());
                requests.add(req);
            }
        }
        return ResponseEntity.ok(requests);
    }

    /**
     * POST /api/faculty/enrollment-requests/{id}/approve
     */
    @PostMapping("/enrollment-requests/{id}/approve")
    @PreAuthorize("hasAuthority('ROLE_FACULTY')")
    public ResponseEntity<?> approveRequest(@PathVariable Long id) {
        try {
            slotEnrollmentAgent.approveEnrollment(id);
            return ResponseEntity.ok(Map.of("message", "Approved"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/faculty/enrollment-requests/{id}/reject
     */
    @PostMapping("/enrollment-requests/{id}/reject")
    @PreAuthorize("hasAuthority('ROLE_FACULTY')")
    public ResponseEntity<?> rejectRequest(@PathVariable Long id) {
        try {
            slotEnrollmentAgent.rejectEnrollment(id);
            return ResponseEntity.ok(Map.of("message", "Rejected"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/faculty/mark-attendance
     * Payload: { offeringId: Long, date: String, records: [{ studentId, present }]
     * }
     */
    @PostMapping("/mark-attendance")
    @PreAuthorize("hasAuthority('ROLE_FACULTY')")
    public ResponseEntity<?> markAttendance(
            Principal principal,
            @RequestBody Map<String, Object> payload) {

        try {
            Long offeringId = Long.valueOf(payload.get("offeringId").toString());
            String dateStr = payload.get("date").toString();
            Object recordsObj = payload.get("records");
            if (!(recordsObj instanceof List)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid records format"));
            }
            List<?> rawRecords = (List<?>) recordsObj;
            java.time.LocalDate date = java.time.LocalDate.parse(dateStr);

            List<SlotEnrollment> enrollments = slotEnrollmentRepository.findBySubjectOfferingIdAndStatus(offeringId,
                    "ENROLLED");

            for (Object rawRec : rawRecords) {
                if (!(rawRec instanceof Map))
                    continue;
                Map<?, ?> rec = (Map<?, ?>) rawRec;
                Long studentId = Long.valueOf(rec.get("studentId").toString());
                Boolean present = Boolean.valueOf(rec.get("present").toString());

                SlotEnrollment se = enrollments.stream()
                        .filter(e -> e.getStudent().getId().equals(studentId))
                        .findFirst()
                        .orElse(null);

                if (se != null) {
                    List<Attendance> existing = attendanceRepository.findBySlotEnrollmentIdAndDate(se.getId(), date);
                    Attendance att;
                    if (existing.isEmpty()) {
                        att = new Attendance();
                    } else {
                        att = existing.get(0);
                        // Clean up any legacy duplicates
                        if (existing.size() > 1) {
                            for (int i = 1; i < existing.size(); i++) {
                                attendanceRepository.delete(existing.get(i));
                            }
                        }
                    }
                    att.setSlotEnrollment(se);
                    att.setDate(date);
                    att.setPresent(present);
                    attendanceRepository.save(att);

                    // --- [EVENT DRIVEN] Trigger Reactive Audit ---
                    long total = attendanceRepository.countBySlotEnrollmentId(se.getId());
                    long presentCount = attendanceRepository.countBySlotEnrollmentIdAndPresentTrue(se.getId());
                    double pct = (total == 0) ? 100.0 : ((double) presentCount / total) * 100.0;

                    if (pct < 80.0) {
                        universityEventPublisher.publishAttendanceBreach(studentId, pct,
                                se.getSubjectOffering() != null ? se.getSubjectOffering().getSubjectName() : "Unknown");
                    }

                    // --- [V4.1] Trigger Random Absence Nudge ---
                    if (!present) {
                        universityEventPublisher.publishRandomAbsence(studentId, 
                                se.getSubjectOffering() != null ? se.getSubjectOffering().getSubjectName() : "Unknown");
                    }
                }
            }
            return ResponseEntity.ok(Map.of("message", "Attendance marked successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /api/faculty/attendance/{offeringId}
     * Returns a list of students enrolled in the class with their attendance stats.
     */
    @GetMapping("/attendance/{offeringId}")
    @PreAuthorize("hasAuthority('ROLE_FACULTY')")
    public ResponseEntity<?> getAttendanceStats(
            @PathVariable Long offeringId) {

        // Ensure offering belongs to the requesting faculty (omitted for brevity,
        // assume valid for MVP)
        List<SlotEnrollment> enrollments = slotEnrollmentRepository.findBySubjectOfferingIdAndStatus(offeringId,
                "ENROLLED");
        List<Map<String, Object>> result = new ArrayList<>();

        for (SlotEnrollment se : enrollments) {
            long totalClasses = attendanceRepository.countBySlotEnrollmentId(se.getId());
            long presentClasses = attendanceRepository.countBySlotEnrollmentIdAndPresentTrue(se.getId());
            double percentage = totalClasses == 0 ? 0 : ((double) presentClasses / totalClasses) * 100;

            Map<String, Object> map = new LinkedHashMap<>();
            map.put("studentId", se.getStudent().getId());
            map.put("studentName", se.getStudent().getFullName());
            map.put("rollNumber", se.getStudent().getRollNumber());
            map.put("totalClasses", totalClasses);
            map.put("presentClasses", presentClasses);
            map.put("percentage", Math.round(percentage * 10.0) / 10.0); // 1 decimal place
            map.put("examEligible", se.getExamEligible());
            result.add(map);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * POST /api/faculty/exam-eligibility
     * Payload: { offeringId: Long, eligibleStudentIds: [Long] }
     */
    @PostMapping("/exam-eligibility")
    @PreAuthorize("hasAuthority('ROLE_FACULTY')")
    public ResponseEntity<?> setExamEligibility(@RequestBody Map<String, Object> payload) {
        try {
            Long offeringId = Long.valueOf(payload.get("offeringId").toString());
            Object rawList = payload.get("eligibleStudentIds");
            List<Long> eligibleStudentIds = new ArrayList<>();

            if (rawList instanceof List) {
                List<?> list = (List<?>) rawList;
                for (Object item : list) {
                    if (item instanceof Number) {
                        eligibleStudentIds.add(((Number) item).longValue());
                    }
                }
            }

            List<SlotEnrollment> enrollments = slotEnrollmentRepository.findBySubjectOfferingIdAndStatus(offeringId,
                    "ENROLLED");
            for (SlotEnrollment se : enrollments) {
                boolean isEligible = eligibleStudentIds.contains(se.getStudent().getId());
                se.setExamEligible(isEligible);
                slotEnrollmentRepository.save(se);
            }
            return ResponseEntity.ok(Map.of("message", "Eligibility successfully updated"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/faculty/proceed-to-exam/{offeringId}
     */
    @PostMapping("/proceed-to-exam/{offeringId}")
    @PreAuthorize("hasAuthority('ROLE_FACULTY')")
    public ResponseEntity<?> proceedToExam(
            @PathVariable Long offeringId) {
        try {
            SubjectOffering offering = subjectOfferingRepository.findById(offeringId)
                    .orElseThrow(() -> new RuntimeException("Offering not found"));

            if (Boolean.TRUE.equals(offering.getLockedForExam())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Offering is already locked for exams"));
            }

            offering.setLockedForExam(true);
            subjectOfferingRepository.save(offering);

            // Trigger AI Agent Exam Scheduling Event
            eventPublisher.publishEvent(new com.unios.domain.events.ProceedToExamEvent(this, offeringId));

            return ResponseEntity
                    .ok(Map.of("message", "Proceeded to exam successfully. AI Agent is scheduling exams."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    /**
     * GET /api/faculty/attendance/absent-today
     * Returns a list of students marked absent today along with their AI-collected reasons.
     */
    @GetMapping("/attendance/absent-today")
    @PreAuthorize("hasAuthority('ROLE_FACULTY')")
    public ResponseEntity<?> getAbsencesWithReasons() {
        java.time.LocalDate today = java.time.LocalDate.now();
        List<Attendance> absences = attendanceRepository.findByDateAndPresentFalse(today);
        
        // Use a Map to deduplicate by studentId-subject
        Map<String, Map<String, Object>> deduplicated = new LinkedHashMap<>();
        
        for (Attendance a : absences) {
            SlotEnrollment se = a.getSlotEnrollment();
            if (se == null || se.getStudent() == null) continue;
            
            String subject = se.getSubjectOffering() != null ? se.getSubjectOffering().getSubjectName() : "Unknown";
            String key = se.getStudent().getId() + "-" + subject;
            
            if (!deduplicated.containsKey(key)) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("studentName", se.getStudent().getFullName());
                entry.put("subject", subject);
                
                System.out.println("[DIAGNOSTIC] Processing absence for: " + se.getStudent().getFullName() + 
                                   " | Phone: " + se.getStudent().getParentPhone() + 
                                   " | Parent: " + se.getStudent().getParentName());
                
                // Find most recent reason for this student & subject today
                java.time.LocalDateTime startOfDay = today.atStartOfDay();
                List<AbsenceReason> reasons = absenceReasonRepository.findByStudentIdAndSubjectNameAndTimestampAfter(
                    se.getStudent().getId(), 
                    subject, 
                    startOfDay
                );
                
                entry.put("reason", reasons.isEmpty() ? "Call in progress or pending..." : reasons.get(reasons.size()-1).getReason());
                deduplicated.put(key, entry);
            }
        }
        
        return ResponseEntity.ok(new ArrayList<>(deduplicated.values()));
    }

    /**
     * POST /api/faculty/update-profile
     */
    @PostMapping("/update-profile")
    @PreAuthorize("hasAnyAuthority('ROLE_FACULTY', 'ROLE_ADMIN')")
    public ResponseEntity<?> updateProfile(Principal principal, @RequestBody Map<String, String> body) {
        User user = userRepository.findByEmail(principal.getName()).orElse(null);
        if (user == null) return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        
        Faculty faculty = facultyRepository.findByUserId(user.getId()).orElse(null);
        if (faculty == null) return ResponseEntity.status(404).body(Map.of("error", "Faculty profile not found"));

        if (body.containsKey("fullName")) faculty.setFullName(body.get("fullName"));
        if (body.containsKey("department")) faculty.setDepartment(body.get("department"));
        
        facultyRepository.save(faculty);
        return ResponseEntity.ok(Map.of("message", "Profile updated successfully"));
    }

    /**
     * POST /api/faculty/update-password
     */
    @PostMapping("/update-password")
    @PreAuthorize("hasAnyAuthority('ROLE_FACULTY', 'ROLE_ADMIN')")
    public ResponseEntity<?> updatePassword(Principal principal, @RequestBody Map<String, String> body) {
        User user = userRepository.findByEmail(principal.getName()).orElse(null);
        if (user == null) return ResponseEntity.status(404).body(Map.of("error", "User not found"));

        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("newPassword");

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            return ResponseEntity.status(400).body(Map.of("error", "Incorrect old password"));
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        
        return ResponseEntity.ok(Map.of("message", "Password updated successfully"));
    }

    /**
     * DELETE /api/faculty/debug/clear-today
     */
    @DeleteMapping("/debug/clear-today")
    @PreAuthorize("hasAuthority('ROLE_FACULTY')")
    public ResponseEntity<?> clearTodayAttendance() {
        java.time.LocalDate today = java.time.LocalDate.now();
        
        // Clear Attendance
        List<Attendance> records = attendanceRepository.findByDate(today);
        attendanceRepository.deleteAll(records);
        
        // Clear AI Reasons for today
        java.time.LocalDateTime startOfDay = today.atStartOfDay();
        List<AbsenceReason> reasons = absenceReasonRepository.findByTimestampAfter(startOfDay);
        absenceReasonRepository.deleteAll(reasons);
        
        return ResponseEntity.ok(Map.of("message", "Cleared " + records.size() + " attendance and " + reasons.size() + " AI reasons for today."));
    }

    /**
     * GET /api/faculty/debug/check-student/{name}
     */
    @GetMapping("/debug/check-student/{name}")
    @PreAuthorize("hasAuthority('ROLE_FACULTY')")
    public ResponseEntity<?> checkStudent(@PathVariable String name) {
        return ResponseEntity.ok(studentRepository.findAll().stream()
            .filter(s -> s.getFullName().toLowerCase().contains(name.toLowerCase()))
            .map(s -> Map.of(
                "id", s.getId(),
                "name", s.getFullName(),
                "phone", s.getParentPhone() != null ? s.getParentPhone() : "MISSING",
                "parent", s.getParentName() != null ? s.getParentName() : "MISSING"
            )).toList());
    }
}
