package com.unios.service.agents.academics;

import com.unios.model.SlotEnrollment;
import com.unios.model.StudentCredit;
import com.unios.repository.SlotEnrollmentRepository;
import com.unios.repository.StudentCreditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreditTrackerAgent {

    private final SlotEnrollmentRepository slotEnrollmentRepository;
    private final StudentCreditRepository studentCreditRepository;
    private final com.unios.service.agents.graduation.GraduationEligibilityAgent graduationEligibilityAgent;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    public CreditTrackerAgent(SlotEnrollmentRepository slotEnrollmentRepository,
            StudentCreditRepository studentCreditRepository,
            com.unios.service.agents.graduation.GraduationEligibilityAgent graduationEligibilityAgent,
            org.springframework.context.ApplicationEventPublisher eventPublisher) {
        this.slotEnrollmentRepository = slotEnrollmentRepository;
        this.studentCreditRepository = studentCreditRepository;
        this.graduationEligibilityAgent = graduationEligibilityAgent;
        this.eventPublisher = eventPublisher;
    }

    @org.springframework.context.event.EventListener
    @Transactional
    public void onAcademicCompletion(com.unios.domain.events.AcademicCompletionEvent event) {
        processCompletion(event.getSlotEnrollmentId(), event.getMarks());
    }

    @Transactional
    public void processCompletion(Long slotEnrollmentId, int marks) {
        SlotEnrollment enrollment = slotEnrollmentRepository.findById(slotEnrollmentId)
                .orElseThrow(() -> new RuntimeException("Enrollment not found"));

        com.unios.model.Student student = enrollment.getStudent();
        String subjectName = enrollment.getSubjectOffering().getSubjectName();

        boolean passed = marks >= 40;
        enrollment.setStatus(passed ? "PASSED" : "FAILED");
        slotEnrollmentRepository.save(enrollment);

        if (passed) {
            StudentCredit credit = studentCreditRepository.findByStudentId(student.getId())
                    .orElseGet(() -> {
                        StudentCredit newCredit = new StudentCredit();
                        newCredit.setStudent(student);
                        newCredit.setEarnedCredits(0);
                        newCredit.setPendingCredits(0);
                        return studentCreditRepository.save(newCredit);
                    });

            int earned = credit.getEarnedCredits() + enrollment.getSubjectOffering().getCredits();
            credit.setEarnedCredits(earned);
            studentCreditRepository.save(credit);

            System.out.println("[ACADEMICS] Student " + student.getId() + " PASSED " + subjectName +
                    ". Total Credits = " + earned);

            eventPublisher.publishEvent(new com.unios.domain.events.CreditsUpdatedEvent(this, student.getId(), earned));

            if (earned >= 160) {
                System.out.println(
                        "[ACADEMICS] Student " + student.getId() + " achieved 160 credits. Identifying as Candidate.");
                graduationEligibilityAgent.markAsCandidate(student.getId());
                eventPublisher
                        .publishEvent(new com.unios.domain.events.GraduationCandidateEvent(this, student.getId()));
            }
        } else {
            System.out.println("[ACADEMICS] Student " + student.getId() + " FAILED " + subjectName + ".");
        }
    }
}
