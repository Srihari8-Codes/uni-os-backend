package com.unios.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/dev")
public class DevResetController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostMapping("/reset-database")
    public ResponseEntity<?> resetDatabase() {
        System.out.println("LOW-LEVEL DATABASE WIPE INITIATED...");
        
        String[] tables = {
            "activity_logs", "agent_action_outcome", "agent_audit_log", "agent_decision_log",
            "agent_experience", "agent_strategy", "agent_task", "email_log", 
            "absence_reason", "attendance", "attendance_intervention", "assessment", 
            "exam_submission", "entrance_exam_session", "exam_result", "exam_questions",
            "exam_hall", "exam_schedules", "exam_schedule", "enrollment", 
            "counseling_session", "applications", "candidate", "student", 
            "join_request", "admission_weights", "batch_programs", "batches", 
            "faculty", "staff", "university_workflow_state", "workflow_state",
            "program", "subject_offering", "universities", "users"
        };

        StringBuilder logs = new StringBuilder();
        for (String table : tables) {
            try {
                // Use a direct delete without a global transaction to avoid silent rollbacks
                jdbcTemplate.execute("DELETE FROM " + table);
                logs.append("CLEANED: ").append(table).append("\n");
            } catch (Exception e) {
                logs.append("SKIPPED: ").append(table).append(" (").append(e.getMessage().split("\n")[0]).append(")\n");
            }
        }

        return ResponseEntity.ok(Map.of(
            "message", "Database cleanup performed.",
            "details", logs.toString()
        ));
    }
}
