package com.unios.controller;

import com.unios.model.SlotEnrollment;
import com.unios.model.Student;
import com.unios.model.CourseExam;
import com.unios.repository.CourseExamRepository;
import com.unios.repository.SlotEnrollmentRepository;
import com.unios.repository.StudentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/course-exams")
@CrossOrigin(origins = "http://localhost:5173")
public class CourseExamController {

    private final CourseExamRepository courseExamRepository;
    private final SlotEnrollmentRepository slotEnrollmentRepository;
    private final StudentRepository studentRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CourseExamController(CourseExamRepository courseExamRepository,
                                SlotEnrollmentRepository slotEnrollmentRepository,
                                StudentRepository studentRepository) {
        this.courseExamRepository = courseExamRepository;
        this.slotEnrollmentRepository = slotEnrollmentRepository;
        this.studentRepository = studentRepository;
    }

    @GetMapping
    public ResponseEntity<List<CourseExam>> getAllExams() {
        return ResponseEntity.ok(courseExamRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CourseExam> getExam(@PathVariable Long id) {
        return courseExamRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/by-offering/{offeringId}")
    public ResponseEntity<CourseExam> getByOffering(@PathVariable Long offeringId) {
        return courseExamRepository.findBySubjectOfferingId(offeringId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Exam Portal Endpoints (Expected by StudentExam.jsx) ──────

    @GetMapping("/questions/{offeringId}")
    public ResponseEntity<?> getQuestionsForPortal(@PathVariable Long offeringId) {
        CourseExam exam = courseExamRepository.findBySubjectOfferingId(offeringId)
                .orElseThrow(() -> new RuntimeException("Exam not found for this offering"));

        try {
            JsonNode root = objectMapper.readTree(exam.getQuestionsJson());
            List<Map<String, Object>> mappedQuestions = new ArrayList<>();
            
            int i = 0;
            for (JsonNode qNode : root) {
                Map<String, Object> q = new HashMap<>();
                q.put("id", i++);
                q.put("questionText", qNode.get("question").asText());
                JsonNode opts = qNode.get("options");
                q.put("optionA", opts.get(0).asText());
                q.put("optionB", opts.get(1).asText());
                q.put("optionC", opts.get(2).asText());
                q.put("optionD", opts.get(3).asText());
                mappedQuestions.add(q);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("subjectName", exam.getSubjectOffering().getSubjectName());
            response.put("examDate", exam.getStartTime().toString());
            response.put("room", exam.getExamHall().getName());
            response.put("questions", mappedQuestions);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to parse exam questions."));
        }
    }

    @PostMapping("/submit/{offeringId}")
    public ResponseEntity<?> submitExam(@PathVariable Long offeringId, @RequestBody Map<String, Map<String, String>> payload) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Student student = studentRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        SlotEnrollment enrollment = slotEnrollmentRepository.findByStudentIdAndSubjectOfferingId(student.getId(), offeringId)
                .orElseThrow(() -> new RuntimeException("Enrollment not found"));

        CourseExam exam = courseExamRepository.findBySubjectOfferingId(offeringId)
                .orElseThrow(() -> new RuntimeException("Exam not found"));

        Map<String, String> answers = payload.get("answers");
        int correctCount = 0;
        int totalQuestions = 0;

        try {
            JsonNode root = objectMapper.readTree(exam.getQuestionsJson());
            totalQuestions = root.size();
            int i = 0;
            for (JsonNode qNode : root) {
                String studentAnswer = answers.get(String.valueOf(i));
                int correctIdx = qNode.get("correctIndex").asInt();
                String correctLetter = String.valueOf((char)('A' + correctIdx));

                if (correctLetter.equals(studentAnswer)) {
                    correctCount++;
                }
                i++;
            }

            double score = (double) correctCount / totalQuestions * 100;
            enrollment.setExamMarks(score);
            
            boolean passed = score >= 50.0;
            if (passed) {
                enrollment.setStatus("PASSED");
                enrollment.setCreditsEarned(exam.getSubjectOffering().getCredits());
                // Add credits to student record
                student.setCreditsEarned((student.getCreditsEarned() == null ? 0 : student.getCreditsEarned()) + exam.getSubjectOffering().getCredits());
                studentRepository.save(student);
            } else {
                enrollment.setStatus("FAILED");
                enrollment.setCreditsEarned(0);
            }
            slotEnrollmentRepository.save(enrollment);

            return ResponseEntity.ok(Map.of(
                "passed", passed,
                "score", (int)score,
                "correct", correctCount,
                "total", totalQuestions,
                "message", passed ? "Congratulations! You have passed and earned " + exam.getSubjectOffering().getCredits() + " credits." : "You did not pass. Please contact your mentor for re-examination details."
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Grading failed."));
        }
    }
}
