package com.unios.controller;

import com.unios.model.StudentOutcome;
import com.unios.model.Student;
import com.unios.repository.StudentOutcomeRepository;
import com.unios.repository.StudentRepository;
import com.unios.service.admissions.ReflectionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/admin/outcomes")
public class StudentOutcomeController {

    private final StudentOutcomeRepository studentOutcomeRepository;
    private final StudentRepository studentRepository;
    private final ReflectionService reflectionService;

    public StudentOutcomeController(StudentOutcomeRepository studentOutcomeRepository,
                                    StudentRepository studentRepository,
                                    ReflectionService reflectionService) {
        this.studentOutcomeRepository = studentOutcomeRepository;
        this.studentRepository = studentRepository;
        this.reflectionService = reflectionService;
    }

    @PostMapping("/{studentId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> saveOutcome(@PathVariable Long studentId, @RequestBody Map<String, Double> payload) {
        Student student = studentRepository.findById(studentId).orElse(null);
        if (student == null) {
            return ResponseEntity.notFound().build();
        }

        StudentOutcome outcome = studentOutcomeRepository.findByStudentId(studentId)
                .orElse(new StudentOutcome());
        
        outcome.setStudent(student);
        
        if (payload.containsKey("attendancePct")) {
            outcome.setAttendancePct(payload.get("attendancePct"));
        }
        if (payload.containsKey("examScore")) {
            outcome.setFirstExamScore(payload.get("examScore"));
        }

        studentOutcomeRepository.save(outcome);
        return ResponseEntity.ok(Map.of("message", "Outcome saved successfully", "student", student.getFullName()));
    }

    @GetMapping("/batch/{batchId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getBatchOutcomes(@PathVariable Long batchId) {
        // Find all enrolled students for this batch, then join with outcomes if they exist
        List<Student> enrolledStudents = studentRepository.findByBatchId(batchId);
        
        List<Map<String, Object>> response = enrolledStudents.stream().map(student -> {
            Optional<StudentOutcome> outcomeOpt = studentOutcomeRepository.findByStudentId(student.getId());
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("studentId", student.getId());
            map.put("name", student.getFullName());
            map.put("riskLevel", student.getRiskLevel() != null ? student.getRiskLevel() : "UNASSIGNED");
            map.put("attendancePct", outcomeOpt.map(StudentOutcome::getAttendancePct).orElse(0.0));
            map.put("examScore", outcomeOpt.map(StudentOutcome::getFirstExamScore).orElse(0.0));
            map.put("confidenceScore", (student.getApplication() != null && student.getApplication().getConfidenceScore() != null) ? student.getApplication().getConfidenceScore() : 50.0);
            return map;
        }).collect(java.util.stream.Collectors.toList());

        return ResponseEntity.ok(response);
    }

    // Phase 6: Manual trigger for reflection engine
    @PostMapping("/reflect/{batchId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> triggerReflection(@PathVariable Long batchId) {
        try {
            Map<String, Object> result = reflectionService.reflectAndAdjustWeights(batchId);
            if ("SKIPPED".equals(result.get("status"))) {
                return ResponseEntity.badRequest().body(result);
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
