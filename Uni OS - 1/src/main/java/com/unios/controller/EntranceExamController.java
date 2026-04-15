package com.unios.controller;

import com.unios.model.Application;
import com.unios.service.admissions.EntranceExamService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/entrance-exam")
public class EntranceExamController {

    private final EntranceExamService examService;

    public EntranceExamController(EntranceExamService examService) {
        this.examService = examService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials) {
        try {
            String appId = credentials.get("appId");
            String password = credentials.get("password");
            Application app = examService.login(appId, password);
            return ResponseEntity.ok(Map.of(
                "appId", "APP-" + app.getId(),
                "fullName", app.getFullName(),
                "email", app.getEmail(),
                "batchName", app.getBatch().getName(),
                "status", app.getStatus()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(e.getMessage());
        }
    }

    @GetMapping("/questions")
    public ResponseEntity<List<EntranceExamService.Question>> getQuestions() {
        return ResponseEntity.ok(examService.getQuestions());
    }

    @PostMapping("/submit")
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> submitExam(@RequestBody Map<String, Object> submission) {
        try {
            String appId = (String) submission.get("appId");
            Map<String, String> answers = (Map<String, String>) submission.get("answers");
            Map<String, Object> result = examService.submitExam(appId, answers);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
