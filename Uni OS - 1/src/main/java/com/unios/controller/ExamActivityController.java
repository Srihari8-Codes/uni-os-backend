package com.unios.controller;

import com.unios.model.Application;
import com.unios.repository.ApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/exams")
@RequiredArgsConstructor
@Slf4j
public class ExamActivityController {

    private final ApplicationRepository applicationRepository;

    /**
     * Start the exam session.
     */
    @PostMapping("/start")
    public ResponseEntity<?> startExam(@RequestBody Map<String, String> payload) {
        String email = payload.get("email");
        String password = payload.get("password");

        return applicationRepository.findFirstByEmailAndStatus(email, "EXAM_SCHEDULED")
            .map(app -> {
                if (password.equals(app.getExamPassword())) {
                    app.setStatus("EXAM_IN_PROGRESS");
                    applicationRepository.save(app);
                    log.info("[EXAM] Student {} started the exam.", app.getFullName());
                    return ResponseEntity.ok(Map.of("message", "Exam started. Integrity monitoring active."));
                }
                return ResponseEntity.status(401).body("Invalid credentials.");
            })
            .orElse(ResponseEntity.badRequest().body("No scheduled exam found or invalid email."));
    }

    /**
     * Terminate exam due to integrity violation (Anti-Cheat).
     */
    @PostMapping("/suspect-activity")
    public ResponseEntity<?> handleSuspectActivity(@RequestBody Map<String, String> payload) {
        String email = payload.get("email");
        String appIdStr = payload.get("appId");
        String reason = payload.getOrDefault("reason", "Tab Switch / Window Blur");

        log.info("[EXAM] Suspect activity reported for Email: {}, AppId: {}. Reason: {}", email, appIdStr, reason);

        java.util.Optional<Application> appOpt = java.util.Optional.empty();

        // 1. Try finding by Email first
        if (email != null && !email.isEmpty()) {
            appOpt = applicationRepository.findFirstByEmailAndStatus(email, "EXAM_IN_PROGRESS");
        }

        // 2. Fallback to App ID if email fails or is missing
        if (appOpt.isEmpty() && appIdStr != null && !appIdStr.isEmpty()) {
            try {
                Long id = Long.parseLong(appIdStr.replaceAll("[^0-9]", ""));
                appOpt = applicationRepository.findById(id);
            } catch (Exception e) {
                log.warn("[EXAM] Invalid AppId format in suspect activity: {}", appIdStr);
            }
        }

        return appOpt.map(app -> {
            app.setStatus("EXAM_FAILED");
            app.setOcrAuditLog("Integrity Violation: " + reason);
            applicationRepository.save(app);
            log.warn("[EXAM] INTEGRITY VIOLATION confirmed for {}. Status: FAIL.", app.getFullName());
            return ResponseEntity.ok(Map.of("message", "Exam terminated."));
        }).orElse(ResponseEntity.status(404).body(Map.of("error", "Candidate session not found.")));
    }

    /**
     * Normal exam submission.
     */
    @PostMapping("/submit")
    public ResponseEntity<?> submitExam(@RequestBody Map<String, Object> payload) {
        String email = (String) payload.get("email");
        Double score = Double.valueOf(payload.getOrDefault("score", 0.0).toString());

        return applicationRepository.findFirstByEmailAndStatus(email, "EXAM_IN_PROGRESS")
            .map(app -> {
                app.setStatus("EXAM_COMPLETED");
                app.setAcademicScore(score); // Save exam score here (or in ExamResult)
                applicationRepository.save(app);
                log.info("[EXAM] Student {} submitted the exam. Score: {}", app.getFullName(), score);
                return ResponseEntity.ok(Map.of("message", "Exam submitted successfully."));
            })
            .orElse(ResponseEntity.notFound().build());
    }
}
