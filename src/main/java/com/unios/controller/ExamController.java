package com.unios.controller;

import com.unios.model.*;
import com.unios.repository.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/exam")
public class ExamController {

    private final ExamQuestionRepository examQuestionRepository;
    private final ExamSubmissionRepository examSubmissionRepository;
    private final SlotEnrollmentRepository slotEnrollmentRepository;
    private final SubjectOfferingRepository subjectOfferingRepository;
    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final SemesterExamScheduleRepository semesterExamScheduleRepository;

    public ExamController(ExamQuestionRepository examQuestionRepository,
            ExamSubmissionRepository examSubmissionRepository,
            SlotEnrollmentRepository slotEnrollmentRepository,
            SubjectOfferingRepository subjectOfferingRepository,
            UserRepository userRepository,
            StudentRepository studentRepository,
            SemesterExamScheduleRepository semesterExamScheduleRepository) {
        this.examQuestionRepository = examQuestionRepository;
        this.examSubmissionRepository = examSubmissionRepository;
        this.slotEnrollmentRepository = slotEnrollmentRepository;
        this.subjectOfferingRepository = subjectOfferingRepository;
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.semesterExamScheduleRepository = semesterExamScheduleRepository;
    }

    /**
     * GET /api/exam/{offeringId}/questions
     * Returns the 10 MCQ questions for this subject offering.
     * Only accessible if the student is enrolled and exam-eligible.
     */
    @GetMapping("/{offeringId}/questions")
    @PreAuthorize("hasAuthority('ROLE_STUDENT')")
    public ResponseEntity<?> getQuestions(@PathVariable Long offeringId, Principal principal) {
        User user = userRepository.findByEmail(principal.getName()).orElse(null);
        if (user == null)
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

        Student student = studentRepository.findByUserId(user.getId()).orElse(null);
        if (student == null)
            return ResponseEntity.status(404).body(Map.of("error", "Student profile not found"));

        // Verify the student is enrolled and exam-eligible
        SlotEnrollment enrollment = slotEnrollmentRepository
                .findByStudentIdAndSubjectOfferingId(student.getId(), offeringId)
                .orElse(null);

        if (enrollment == null) {
            return ResponseEntity.status(403).body(Map.of("error", "You are not enrolled in this subject"));
        }
        if (!Boolean.TRUE.equals(enrollment.getExamEligible())) {
            return ResponseEntity.status(403).body(Map.of("error", "You are not eligible for this exam"));
        }

        // Check if already submitted
        if (examSubmissionRepository.existsBySlotEnrollmentId(enrollment.getId())) {
            return ResponseEntity.status(400)
                    .body(Map.of("error", "You have already submitted this exam", "submitted", true));
        }

        // Get exam schedule for subject/date info
        SemesterExamSchedule schedule = semesterExamScheduleRepository.findBySubjectOfferingId(offeringId).orElse(null);
        if (schedule == null || !"APPROVED".equals(schedule.getStatus())) {
            return ResponseEntity.status(403).body(Map.of("error", "Exam has not been approved by admin yet"));
        }

        List<ExamQuestion> questions = examQuestionRepository.findBySubjectOfferingId(offeringId);
        if (questions.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "No questions found for this exam"));
        }

        // Return questions (without revealing correctOption)
        List<Map<String, Object>> result = new ArrayList<>();
        for (ExamQuestion q : questions) {
            Map<String, Object> qMap = new LinkedHashMap<>();
            qMap.put("id", q.getId());
            qMap.put("questionText", q.getQuestionText());
            qMap.put("optionA", q.getOptionA());
            qMap.put("optionB", q.getOptionB());
            qMap.put("optionC", q.getOptionC());
            qMap.put("optionD", q.getOptionD());
            result.add(qMap);
        }

        SubjectOffering offering = subjectOfferingRepository.findById(offeringId).orElse(null);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("subjectName", offering != null ? offering.getSubjectName() : "Unknown");
        response.put("examDate", schedule.getExamDate());
        response.put("room", schedule.getRoom());
        response.put("enrollmentId", enrollment.getId());
        response.put("questions", result);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/exam/{offeringId}/submit
     * Submits exam answers and calculates the score.
     * Body: { "answers": { "questionId": "chosenOption" } }
     */
    @PostMapping("/{offeringId}/submit")
    @PreAuthorize("hasAuthority('ROLE_STUDENT')")
    public ResponseEntity<?> submitExam(@PathVariable Long offeringId,
            @RequestBody Map<String, Object> payload,
            Principal principal) {
        User user = userRepository.findByEmail(principal.getName()).orElse(null);
        if (user == null)
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

        Student student = studentRepository.findByUserId(user.getId()).orElse(null);
        if (student == null)
            return ResponseEntity.status(404).body(Map.of("error", "Student profile not found"));

        SlotEnrollment enrollment = slotEnrollmentRepository
                .findByStudentIdAndSubjectOfferingId(student.getId(), offeringId)
                .orElse(null);

        if (enrollment == null) {
            return ResponseEntity.status(403).body(Map.of("error", "You are not enrolled in this subject"));
        }
        if (!Boolean.TRUE.equals(enrollment.getExamEligible())) {
            return ResponseEntity.status(403).body(Map.of("error", "You are not eligible for this exam"));
        }
        if (examSubmissionRepository.existsBySlotEnrollmentId(enrollment.getId())) {
            return ResponseEntity.status(400).body(Map.of("error", "You have already submitted this exam"));
        }

        // Parse and grade answers
        @SuppressWarnings("unchecked")
        Map<String, String> answers = (Map<String, String>) payload.get("answers");
        if (answers == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "answers field is required"));
        }

        List<ExamQuestion> questions = examQuestionRepository.findBySubjectOfferingId(offeringId);
        int correct = 0;
        for (ExamQuestion q : questions) {
            String chosen = answers.get(String.valueOf(q.getId()));
            if (q.getCorrectOption().equalsIgnoreCase(chosen)) {
                correct++;
            }
        }

        int total = questions.size();
        int score = total > 0 ? (correct * 100) / total : 0;

        // Save submission
        String answersJson = answers.toString();
        ExamSubmission submission = new ExamSubmission();
        submission.setSlotEnrollment(enrollment);
        submission.setAnswers(answersJson);
        submission.setScore(score);
        submission.setSubmittedAt(LocalDateTime.now());
        examSubmissionRepository.save(submission);

        // Save score on slot enrollment too for admin/faculty to see
        enrollment.setMarks(score);
        slotEnrollmentRepository.save(enrollment);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("score", score);
        response.put("correct", correct);
        response.put("total", total);
        response.put("passed", score >= 50);
        response.put("message", score >= 50 ? "Congratulations! You passed the exam."
                : "You did not pass. Don't give up — you can retake this subject.");
        return ResponseEntity.ok(response);
    }
}
