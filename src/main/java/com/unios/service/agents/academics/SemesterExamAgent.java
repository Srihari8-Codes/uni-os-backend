package com.unios.service.agents.academics;

import com.unios.domain.events.AcademicCompletionEvent;
import com.unios.domain.events.ProceedToExamEvent;
import com.unios.model.*;
import com.unios.repository.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

@Service
public class SemesterExamAgent {

    private final SubjectOfferingRepository subjectOfferingRepository;
    private final SlotEnrollmentRepository slotEnrollmentRepository;
    private final SemesterExamScheduleRepository scheduleRepository;
    private final AgentDecisionLogRepository agentDecisionLogRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ExamQuestionRepository examQuestionRepository;
    private final ExamSubmissionRepository examSubmissionRepository;
    private final StudentCreditRepository studentCreditRepository;
    private final com.unios.repository.QuestionBankRepository questionBankRepository;
    private final Random random = new Random();

    public SemesterExamAgent(SubjectOfferingRepository subjectOfferingRepository,
            SlotEnrollmentRepository slotEnrollmentRepository,
            SemesterExamScheduleRepository scheduleRepository,
            AgentDecisionLogRepository agentDecisionLogRepository,
            ApplicationEventPublisher eventPublisher,
            ExamQuestionRepository examQuestionRepository,
            ExamSubmissionRepository examSubmissionRepository,
            StudentCreditRepository studentCreditRepository,
            com.unios.repository.QuestionBankRepository questionBankRepository) {
        this.subjectOfferingRepository = subjectOfferingRepository;
        this.slotEnrollmentRepository = slotEnrollmentRepository;
        this.scheduleRepository = scheduleRepository;
        this.agentDecisionLogRepository = agentDecisionLogRepository;
        this.eventPublisher = eventPublisher;
        this.examQuestionRepository = examQuestionRepository;
        this.examSubmissionRepository = examSubmissionRepository;
        this.studentCreditRepository = studentCreditRepository;
        this.questionBankRepository = questionBankRepository;
    }

    /**
     * PHASE 1: Faculty taps "Proceed to Exam".
     * AI schedules the exam and sets status = PENDING_ADMIN.
     * Seeds 10 dummy questions immediately.
     * Does NOT generate results yet — waits for admin approval + students to take
     * the exam.
     */
    @EventListener
    @Transactional
    public void handleProceedToExam(ProceedToExamEvent event) {
        Long offeringId = event.getOfferingId();
        SubjectOffering offering = subjectOfferingRepository.findById(offeringId).orElse(null);
        if (offering == null)
            return;

        // Schedule exam first
        scheduleExam(offering);
        
        // Generate real questions from the newly created QuestionBank logic
        generateExamFromBank(offering);
    }

    /**
     * PHASE 2: Admin approves the exam schedule.
     * Called by AcademicController after admin clicks "Approve".
     * Sets the schedule to APPROVED so students can access the exam page.
     */
    @Transactional
    public void approveExamSchedule(Long scheduleId) {
        SemesterExamSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new RuntimeException("Exam schedule not found: " + scheduleId));

        schedule.setStatus("APPROVED");
        schedule.setAdminApprovedAt(LocalDateTime.now());
        scheduleRepository.save(schedule);

        AgentDecisionLog log = new AgentDecisionLog(
                "SemesterExamAgent",
                "SemesterExamSchedule",
                scheduleId,
                "Admin Approved Exam Schedule",
                "Exam for " + schedule.getSubjectOffering().getSubjectName()
                        + " approved. Students can now access the exam portal.");
        agentDecisionLogRepository.save(log);

        System.out.println("[SemesterExamAgent] Exam schedule " + scheduleId + " approved by admin.");
    }

    @Transactional
    public void processSemester(Long batchId) {
        System.out.println("[SemesterExamAgent] Triggering semester processing for Batch " + batchId);
        List<SubjectOffering> offerings = subjectOfferingRepository.findByBatchId(batchId);
        for (SubjectOffering so : offerings) {
            try {
                processExamResults(so.getId());
            } catch (Exception e) {
                System.err.println("Failed to process results for offering " + so.getId() + ": " + e.getMessage());
            }
        }
    }

    /**
     * PHASE 3: Admin triggers result processing after exams are done.
     * Uses real ExamSubmission scores — NOT random anymore.
     * PASS (score >= 50): adds credits, keeps enrollment as PASSED.
     * FAIL (score < 50): marks as FAILED and DELETES the enrollment (no-arrear
     * reset).
     */
    @Transactional
    public void processExamResults(Long offeringId) {
        SubjectOffering offering = subjectOfferingRepository.findById(offeringId)
                .orElseThrow(() -> new RuntimeException("Offering not found: " + offeringId));

        System.out.println("[SemesterExamAgent] Processing real results for Offering " + offeringId);

        List<SlotEnrollment> enrollments = slotEnrollmentRepository
                .findBySubjectOfferingIdAndStatus(offeringId, "ENROLLED");

        int passedCount = 0, failedCount = 0, ineligibleCount = 0;

        for (SlotEnrollment se : enrollments) {
            if (!Boolean.TRUE.equals(se.getExamEligible())) {
                // Ineligible — failed by attendance; delete enrollment for no-arrear
                se.setStatus("FAILED");
                slotEnrollmentRepository.save(se);
                slotEnrollmentRepository.delete(se);
                ineligibleCount++;
                continue;
            }

            ExamSubmission submission = examSubmissionRepository
                    .findBySlotEnrollmentId(se.getId()).orElse(null);

            if (submission == null) {
                // Student never showed up — treat as fail
                se.setStatus("FAILED");
                slotEnrollmentRepository.save(se);
                slotEnrollmentRepository.delete(se);
                failedCount++;
                continue;
            }

            int score = submission.getScore();
            boolean passed = score >= 50;

            if (passed) {
                se.setStatus("PASSED");
                se.setMarks(score);
                slotEnrollmentRepository.save(se);
                passedCount++;
                // Award credits via event
                eventPublisher.publishEvent(new AcademicCompletionEvent(this, se.getId(), score));
            } else {
                // FAIL + no-arrear: delete enrollment so student can re-enroll from scratch
                se.setStatus("FAILED");
                se.setMarks(score);
                slotEnrollmentRepository.save(se);
                slotEnrollmentRepository.delete(se);
                failedCount++;
            }
        }

        // Mark schedule as completed
        SemesterExamSchedule schedule = scheduleRepository.findBySubjectOfferingId(offeringId).orElse(null);
        if (schedule != null) {
            schedule.setStatus("COMPLETED");
            scheduleRepository.save(schedule);
        }

        String reasoning = String.format(
                "Results processed from real exam scores. Passed: %d, Failed: %d, Ineligible (no-arrear reset): %d.",
                passedCount, failedCount, ineligibleCount);

        AgentDecisionLog log = new AgentDecisionLog(
                "SemesterExamAgent",
                "SubjectOffering",
                offeringId,
                "Results Processed from Real Exam Submissions",
                reasoning);
        agentDecisionLogRepository.save(log);

        System.out.println("[SemesterExamAgent] " + reasoning);
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    private void scheduleExam(SubjectOffering offering) {
        // Clean up any previous PENDING schedule for this offering
        scheduleRepository.findBySubjectOfferingId(offering.getId()).ifPresent(scheduleRepository::delete);

        SemesterExamSchedule schedule = new SemesterExamSchedule();
        schedule.setSubjectOffering(offering);
        schedule.setExamDate(LocalDate.now().plusDays(10));
        schedule.setRoom("Hall-" + (100 + random.nextInt(900)));
        schedule.setStatus("PENDING_ADMIN"); // Must be approved by admin before students can access

        List<SlotEnrollment> enrollments = slotEnrollmentRepository
                .findBySubjectOfferingIdAndStatus(offering.getId(), "ENROLLED");

        StringBuilder allocations = new StringBuilder("{");
        int seatNumber = 1;
        for (SlotEnrollment se : enrollments) {
            if (Boolean.TRUE.equals(se.getExamEligible())) {
                allocations.append("\"").append(se.getStudent().getRollNumber()).append("\": \"Seat-")
                        .append(seatNumber++).append("\", ");
            }
        }
        if (seatNumber > 1) {
            allocations.setLength(allocations.length() - 2);
        }
        allocations.append("}");

        schedule.setHallAllocations(allocations.toString());
        scheduleRepository.save(schedule);

        AgentDecisionLog log = new AgentDecisionLog(
                "SemesterExamAgent",
                "SubjectOffering",
                offering.getId(),
                "Exam Scheduled — Awaiting Admin Approval",
                "Room: " + schedule.getRoom() + " | Date: " + schedule.getExamDate()
                        + " | Eligible students: " + (seatNumber - 1));
        agentDecisionLogRepository.save(log);
    }

    private void generateExamFromBank(SubjectOffering offering) {
        // Only generate if no questions exist yet for this exact offering
        if (examQuestionRepository.existsBySubjectOfferingId(offering.getId()))
            return;

        List<com.unios.model.QuestionBank> bankQuestions = questionBankRepository.findBySubjectName(offering.getSubjectName());
        
        if (bankQuestions.isEmpty()) {
            System.err.println("[SemesterExamAgent] WARNING: No questions found in QuestionBank for subject: " 
                + offering.getSubjectName() + ". Exam will have 0 questions.");
            return;
        }

        // Shuffle to randomize
        java.util.Collections.shuffle(bankQuestions);
        
        // Pick up to 10 questions
        int questionCount = Math.min(10, bankQuestions.size());
        
        for (int i = 0; i < questionCount; i++) {
            com.unios.model.QuestionBank qb = bankQuestions.get(i);
            ExamQuestion q = new ExamQuestion();
            q.setSubjectOffering(offering);
            q.setQuestionText(qb.getQuestionText());
            q.setOptionA(qb.getOptionA());
            q.setOptionB(qb.getOptionB());
            q.setOptionC(qb.getOptionC());
            q.setOptionD(qb.getOptionD());
            q.setCorrectOption(qb.getCorrectOption());
            examQuestionRepository.save(q);
        }

        System.out.println("[SemesterExamAgent] Generated " + questionCount + " real questions from Bank for: " + offering.getSubjectName());
    }
}
