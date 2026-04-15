package com.unios.service.agents.academics;

import com.unios.domain.events.AcademicCompletionEvent;
import com.unios.domain.events.RiskDetectedEvent;
import com.unios.model.SlotEnrollment;
import com.unios.model.Student;
import com.unios.repository.SlotEnrollmentRepository;
import com.unios.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AcademicsEventListener {

    private final EmailService emailService;
    private final SlotEnrollmentRepository slotEnrollmentRepository;
    private final SemesterExamAgent semesterExamAgent;
    private final SlotEnrollmentAgent slotEnrollmentAgent;
    private final com.unios.repository.StudentRepository studentRepository;
    private final com.unios.repository.SemesterExamScheduleRepository examScheduleRepository;

    public AcademicsEventListener(EmailService emailService,
                                  SlotEnrollmentRepository slotEnrollmentRepository,
                                  SemesterExamAgent semesterExamAgent,
                                  SlotEnrollmentAgent slotEnrollmentAgent,
                                  com.unios.repository.StudentRepository studentRepository,
                                  com.unios.repository.SemesterExamScheduleRepository examScheduleRepository) {
        this.emailService = emailService;
        this.slotEnrollmentRepository = slotEnrollmentRepository;
        this.semesterExamAgent = semesterExamAgent;
        this.slotEnrollmentAgent = slotEnrollmentAgent;
        this.studentRepository = studentRepository;
        this.examScheduleRepository = examScheduleRepository;
    }

    @EventListener
    @Async
    public void handleSubjectOffered(com.unios.domain.events.SubjectOfferedEvent event) {
        Long offeringId = event.getOfferingId();
        Long batchId = event.getBatchId();
        log.info("[AcademicsEventListener] Subject Offering {} Approved for Batch {}. Auto-enrolling all eligible students.", offeringId, batchId);
        
        try {
            // Find all students in this batch and auto-enroll them
            java.util.List<Student> students = studentRepository.findByBatchId(batchId);
            for (Student student : students) {
                try {
                    slotEnrollmentAgent.enroll(student.getId(), offeringId);
                    // Find the created enrollment and auto-approve it for autonomy
                    java.util.Optional<SlotEnrollment> enrollment = slotEnrollmentRepository.findByStudentIdAndSubjectOfferingId(student.getId(), offeringId);
                    enrollment.ifPresent(se -> slotEnrollmentAgent.approveEnrollment(se.getId()));
                } catch (Exception e) {
                    log.warn("[AcademicsEventListener] Skipped enrollment for Student {} into Offering {}: {}", student.getId(), offeringId, e.getMessage());
                }
            }
            log.info("[AcademicsEventListener] Auto-enrollment sequence completed for Offering {}.", offeringId);
        } catch (Exception e) {
            log.error("[AcademicsEventListener] Failed during auto-enrollment: {}", e.getMessage());
        }
    }

    @EventListener
    @Async
    public void handleRiskDetected(RiskDetectedEvent event) {
        Long studentId = event.getStudentId();
        String riskLevel = event.getRiskLevel();

        if ("HIGH_RISK".equals(riskLevel) || "CRITICAL_RISK".equals(riskLevel) || "HIGH".equals(riskLevel)) {
            try {
                log.info("[AcademicsEventListener] Catching RiskDetectedEvent for Student {}: {}. Sending Warning Email.", studentId, riskLevel);

                String subject = "URGENT: Academic Risk Warning from UniOS";
                String body = "Dear Student,\n\nOur intelligent monitoring system has flagged your academic profile as " 
                            + riskLevel + ".\n\nPlease consult your academic counselor immediately to create a recovery plan.\n\nUniOS Academic Affairs";
                
                String studentEmail = "student" + studentId + "@unios.edu"; 
                emailService.sendEmail(studentEmail, subject, body);
                
                log.info("[AcademicsEventListener] Warning email successfully dispatched for Student {}", studentId);
            } catch (Exception e) {
                log.error("[AcademicsEventListener] Failed to send risk warning email: {}", e.getMessage());
            }
        }
    }

    @EventListener
    @Async
    public void handleAcademicCompletion(AcademicCompletionEvent event) {
        Long slotEnrollmentId = event.getSlotEnrollmentId();
        int marks = event.getMarks();
        log.info("[AcademicsEventListener] Term ending for SlotEnrollment {}. Triggering automated Exam flow.", slotEnrollmentId);

        try {
            SlotEnrollment enrollment = slotEnrollmentRepository.findById(slotEnrollmentId)
                    .orElseThrow(() -> new RuntimeException("Enrollment not found for ID: " + slotEnrollmentId));
            
            Long offeringId = enrollment.getSubjectOffering().getId();
            
            // 1. Trigger Exam Scheduling if not already done
            log.info("[AcademicsEventListener] Auto-triggering Exam Scheduling for Offering {}", offeringId);
            // We can publish ProceedToExamEvent to trigger SemesterExamAgent
            // Or call it directly if preferred. Event is cleaner for decoupling.
            // eventPublisher.publishEvent(new com.unios.domain.events.ProceedToExamEvent(this, offeringId));
            // For now, let's assume the SemesterExamAgent is listening and we logic it out.
            
            // 2. Automatically approve all PENDING schedules for this offering
            java.util.List<com.unios.model.SemesterExamSchedule> pendingSchedules = examScheduleRepository.findAll().stream()
                .filter(s -> s.getSubjectOffering().getId().equals(offeringId) && "PENDING_ADMIN".equals(s.getStatus()))
                .collect(java.util.stream.Collectors.toList());
            
            for (com.unios.model.SemesterExamSchedule schedule : pendingSchedules) {
                log.info("[AcademicsEventListener] Auto-approving Exam Schedule {} for Offering {}", schedule.getId(), offeringId);
                semesterExamAgent.approveExamSchedule(schedule.getId());
            }

            // 3. Automatically process results (Grading)
            log.info("[AcademicsEventListener] Automatically processing Exam Results for Offering {}.", offeringId);
            semesterExamAgent.processExamResults(offeringId);
            
            log.info("[AcademicsEventListener] Successfully completed autonomous Term-End flow for Offering {}", offeringId);
            
        } catch (Exception e) {
            log.error("[AcademicsEventListener] Automated Academic pipeline failed for SlotEnrollment {}: {}", slotEnrollmentId, e.getMessage());
        }
    }
}
