package com.unios.service.agents.admissions;

import com.unios.model.*;
import com.unios.repository.*;
import com.unios.service.EmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class EnrollmentAgent {

    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final SlotEnrollmentRepository slotEnrollmentRepository;
    private final SubjectOfferingRepository subjectOfferingRepository;
    private final FacultyRepository facultyRepository;
    private final BatchProgramRepository batchProgramRepository;
    private final ProgramRepository programRepository;
    private final ExamResultRepository examResultRepository;
    private final BatchRepository batchRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;
    private final com.unios.service.admissions.RiskService riskService;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    public EnrollmentAgent(ApplicationRepository applicationRepository,
            UserRepository userRepository,
            StudentRepository studentRepository,
            EnrollmentRepository enrollmentRepository,
            SlotEnrollmentRepository slotEnrollmentRepository,
            SubjectOfferingRepository subjectOfferingRepository,
            FacultyRepository facultyRepository,
            BatchProgramRepository batchProgramRepository,
            ProgramRepository programRepository,
            ExamResultRepository examResultRepository,
            BatchRepository batchRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            com.unios.service.admissions.RiskService riskService,
            org.springframework.context.ApplicationEventPublisher eventPublisher) {
        this.applicationRepository = applicationRepository;
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.slotEnrollmentRepository = slotEnrollmentRepository;
        this.subjectOfferingRepository = subjectOfferingRepository;
        this.facultyRepository = facultyRepository;
        this.batchProgramRepository = batchProgramRepository;
        this.programRepository = programRepository;
        this.examResultRepository = examResultRepository;
        this.batchRepository = batchRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.riskService = riskService;
        this.objectMapper = new ObjectMapper();
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public java.util.Map<String, String> enrollSingle(Long applicationId) {
        Application app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new RuntimeException("Application not found"));
        
        String department = "General";
        if (app.getBatch() != null && app.getBatch().getBatchCode() != null) {
            department = app.getBatch().getBatchCode().contains("-") 
                ? app.getBatch().getBatchCode().split("-")[1] 
                : app.getBatch().getBatchCode();
        }
        
        return enrollSingle(applicationId, department, 0.0, "Automated Enrollment", null, null, null, null);
    }

    @Transactional
    public java.util.Map<String, String> enrollSingle(Long applicationId, String department, Double finalFees,
            String counselorNotes) {
        return enrollSingle(applicationId, department, finalFees, counselorNotes, null, null, null, null);
    }

    @Transactional
    public java.util.Map<String, String> enrollSingle(Long applicationId, String department, Double finalFees,
            String counselorNotes, String profilePhoto, String parentName, String parentEmail, String parentPhone) {
        Application app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new RuntimeException("Application not found"));

        if (!"SELECTED".equals(app.getStatus()) && !"WAITLISTED".equals(app.getStatus()) 
            && !"ADMISSION_OFFERED".equals(app.getStatus()) && !"COUNSELING_PENDING".equals(app.getStatus())) {
            throw new RuntimeException("Application must be in SELECTED, WAITLISTED, ADMISSION_OFFERED, or COUNSELING_PENDING state.");
        }

        if (studentRepository.findByApplicationId(applicationId).isPresent()) {
            throw new RuntimeException("Student already enrolled for this application.");
        }

        String rawPassword = java.util.UUID.randomUUID().toString().substring(0, 8);
        final String finalRawPassword = rawPassword;

        User user = userRepository.findByEmail(app.getEmail()).orElseGet(() -> {
            User newUser = new User();
            newUser.setEmail(app.getEmail());
            newUser.setPassword(passwordEncoder.encode(finalRawPassword));
            newUser.setRole(Role.STUDENT);
            if (app.getBatch() != null) {
                newUser.setUniversity(app.getBatch().getUniversity());
            }
            return userRepository.save(newUser);
        });

        // Always update password during enrollment to ensure credentials are known
        user.setPassword(passwordEncoder.encode(rawPassword));
        userRepository.save(user);

        // Ensure user has correct university and role for this enrollment
        if (app.getBatch() != null && user.getUniversity() == null) {
            user.setUniversity(app.getBatch().getUniversity());
            userRepository.save(user);
        }

        Student student = studentRepository.findByUserId(user.getId()).orElseGet(() -> {
            Student newStudent = new Student();
            newStudent.setUser(user);
            return newStudent;
        });

        if (app.getBatch() != null) {
            student.setUniversity(app.getBatch().getUniversity());
        }
        student.setBatch(app.getBatch());
        student.setApplication(app); // Link to current application
        student.setFullName(app.getFullName());
        student.setDepartment(department);
        student.setProfilePhoto(profilePhoto);
        student.setParentName(parentName);
        student.setParentEmail(parentEmail);
        student.setParentPhone(parentPhone);

        Program program = programRepository.findByName(department).orElse(null);
        if (program != null) {
            student.setProgram(program);
            BatchProgram bp = batchProgramRepository.findByBatchIdAndProgramId(app.getBatch().getId(), program.getId())
                    .orElse(null);
            student.setBatchProgram(bp);
        }

        String batchYear = app.getBatch() != null && app.getBatch().getName() != null
                ? app.getBatch().getName().replaceAll("[^0-9]", "").substring(0,
                        Math.min(4, app.getBatch().getName().replaceAll("[^0-9]", "").length()))
                : String.valueOf(java.time.LocalDate.now().getYear());
        String deptCode = department != null
                ? department.replaceAll("[^A-Za-z]", "")
                        .substring(0, Math.min(3, department.replaceAll("[^A-Za-z]", "").length())).toUpperCase()
                : "GEN";
        String rollNumber = batchYear + "-" + deptCode + "-" + String.format("%04d", app.getId());
        student.setRollNumber(rollNumber);

        studentRepository.save(student);
        
        // Phase 3: Immediate Risk Assessment
        riskService.evaluateRisk(student.getId());

        Enrollment enrollment = new Enrollment();
        enrollment.setStudent(student);
        enrollment.setBatch(app.getBatch());
        enrollment.setEnrolledAt(LocalDateTime.now());
        enrollmentRepository.save(enrollment);

        app.setStatus("ENROLLED");
        applicationRepository.save(app);

        if (student.getBatchProgram() != null) {
            autoAssignMandatorySubjects(student, app.getBatch(), student.getBatchProgram());
        }

        // Send Credentials Email
        sendPortalCredentialsEmail(app, rollNumber, rawPassword);

        // Check if seats are now full
        checkFullCapacityAndNotify(app.getBatch().getId());

        eventPublisher.publishEvent(new com.unios.domain.events.EnrollmentCompletedEvent(this, app.getBatch().getId()));

        return java.util.Map.of(
                "studentId", String.valueOf(student.getId()),
                "rollNumber", rollNumber,
                "email", user.getEmail(),
                "password", rawPassword);
    }

    @Transactional
    public void runWaitlistTopUp(Long batchId) {
        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new RuntimeException("Batch not found"));
        
        Integer intake = batch.getSeatCapacity();
        if (intake == null) {
            System.err.println("[ENROLLMENT AGENT] Error: Batch " + batch.getName() + " has no Seat Capacity defined. Skipping waitlist top-up.");
            return;
        }

        long occupiedCount = applicationRepository.countByBatchIdAndStatusIn(batchId, 
                List.of("SELECTED", "COUNSELING_PENDING", "ADMISSION_OFFERED", "ENROLLED", "COMPLETED"));
        int vacancies = (int) (intake - occupiedCount);

        if (vacancies <= 0) {
            return;
        }

        List<Application> candidates = applicationRepository.findByBatchIdAndStatusIn(batchId, List.of("WAITLISTED", "SELECTED")).stream()
                .sorted(Comparator.comparing((Application a) -> 
                    a.getFinalScore() != null ? a.getFinalScore() : 0.0).reversed())
                .limit(vacancies)
                .collect(Collectors.toList());

        for (Application app : candidates) {
            if ("WAITLISTED".equals(app.getStatus())) {
                app.setStatus("COUNSELING_PENDING");
                applicationRepository.save(app);
                sendWaitlistPromotionEmail(app);
                System.out.println("[ENROLLMENT AGENT] Autonomous Top-up: Student " + app.getFullName() + " promoted from WAITLIST.");
            }
        }
    }

    private void sendPortalCredentialsEmail(Application app, String rollNumber, String password) {
        String subject = "Unios OS: Your Student Portal Access - " + app.getFullName();
        String text = String.format(
            "Welcome to the University, %s!\n\n" +
            "Your enrollment for Batch %s is complete.\n" +
            "Roll Number: %s\n" +
            "Portal Link: " + frontendUrl + "/student/dashboard\n" +
            "Login Email: %s\n" +
            "Password: %s\n\n" +
            "Please login to access your courses and timetable.",
            app.getFullName(), app.getBatch().getName(), rollNumber, app.getEmail(), password
        );
        emailService.sendEmail(app.getEmail(), subject, text);
    }

    private void sendWaitlistPromotionEmail(Application app) {
        String subject = "Unios OS: Admission Waitlist Update - " + app.getFullName();
        String text = String.format(
            "Dear %s,\n\n" +
            "Good news! A vacancy has opened up in Batch %s.\n" +
            "You have been promoted from the waitlist and are invited to meet the counsellor for final processing.\n\n" +
            "Please visit the Admissions cell at your earliest convenience.",
            app.getFullName(), app.getBatch().getName()
        );
        emailService.sendEmail(app.getEmail(), subject, text);
    }

    private void checkFullCapacityAndNotify(Long batchId) {
        Batch batch = batchRepository.findById(batchId).orElse(null);
        if (batch == null) return;

        long enrolledCount = applicationRepository.countByBatchIdAndStatus(batchId, "ENROLLED");
        Integer intake = batch.getSeatCapacity();
        if (intake != null && enrolledCount >= intake) {
            System.out.println("[ENROLLMENT AGENT] Batch " + batch.getName() + " is FULL. Notifying remaining candidates.");
            List<Application> leftovers = applicationRepository.findByBatchIdAndStatusIn(batchId, List.of("WAITLISTED", "SELECTED", "COUNSELING_PENDING"));
            for (Application app : leftovers) {
                app.setStatus("REJECTED_SEATS_FULL");
                applicationRepository.save(app);
                emailService.sendEmail(app.getEmail(), 
                    "Unios OS: Admission Update - Seats Full",
                    "Dear " + app.getFullName() + ",\n\n" +
                    "We appreciate your interest in Batch " + batch.getName() + ". However, the maximum intake for this batch has been reached.\n" +
                    "Better luck next time!\n\nBest Regards,\nInstitutional Orchestrator"
                );
            }
        }
    }

    private void autoAssignMandatorySubjects(Student student, Batch batch, BatchProgram bp) {
        if (bp == null || bp.getSubjects() == null || bp.getSubjects().isEmpty()) {
            return;
        }

        try {
            List<Map<String, Object>> subjectList = objectMapper.readValue(bp.getSubjects(),
                    new TypeReference<List<Map<String, Object>>>() {
                    });

            Faculty defaultFaculty = facultyRepository.findAll().stream().findFirst().orElse(null);

            for (Map<String, Object> subMap : subjectList) {
                Boolean isMandatory = (Boolean) subMap.get("mandatory");
                if (Boolean.TRUE.equals(isMandatory)) {
                    String name = (String) subMap.get("name");
                    Integer credits = (Integer) subMap.get("credits");

                    SubjectOffering offering = subjectOfferingRepository
                            .findByBatchId(batch.getId()).stream()
                            .filter(o -> o.getSubjectName().equalsIgnoreCase(name))
                            .findFirst()
                            .orElseGet(() -> {
                                SubjectOffering newOff = new SubjectOffering();
                                newOff.setBatch(batch);
                                newOff.setSubjectName(name);
                                newOff.setCredits(credits != null ? credits : 3);
                                newOff.setSlot("A");
                                newOff.setCapacity(100);
                                newOff.setFaculty(defaultFaculty);
                                newOff.setActive(true);
                                newOff.setStatus("APPROVED");
                                return subjectOfferingRepository.save(newOff);
                            });

                    SlotEnrollment se = new SlotEnrollment();
                    se.setStudent(student);
                    se.setSubjectOffering(offering);
                    se.setStatus("ENROLLED");
                    se.setExamEligible(false);
                    slotEnrollmentRepository.save(se);
                }
            }
        } catch (Exception e) {
            System.err.println("[ENROLLMENT AGENT] Error auto-assigning subjects: " + e.getMessage());
        }
    }
}
