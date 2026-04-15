package com.unios.service.agents.tools;

import com.unios.model.Student;
import com.unios.repository.ParentNotificationRepository;
import com.unios.repository.StudentRepository;
import com.unios.service.agents.framework.AgentTool;
import com.unios.service.agents.framework.ToolContext;
import com.unios.service.agents.framework.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * AICallerTool (Shadow Edition) — Production-Grade Voice Agent Stub.
 * 
 * This tool is designed to integrate with services like Vapi.ai or Retell AI
 * to hold natural conversations with parents. It is currently in "SHADOW" mode,
 * meaning it logs the intended call but does not initiate a real phone call yet.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AICallerTool implements AgentTool {

    private final StudentRepository studentRepository;

    @Override
    public String name() {
        return "AI_VOICE_CALL";
    }

    @Override
    public String description() {
        return "[SHADOW] Initiates an autonomous AI voice call to the parent. " +
               "Input: {studentId: 1, objective: 'Find reason for 3-day absence'}";
    }

    @Override
    public ToolResult executeWithContext(ToolContext context) {
        Long studentId = ((Number) context.getParameters().get("studentId")).longValue();
        String objective = (String) context.getParameters().get("objective");

        Student student = studentRepository.findById(studentId).orElse(null);
        if (student == null) {
            return ToolResult.builder().summary("Student not found").status("FAILED").build();
        }

        log.info("[SHADOW CALLER] Pre-calculating call strategy for Student: {}", student.getFullName());
        log.info("[SHADOW CALLER] Objective: {}", objective);
        log.info("[SHADOW CALLER] Status: READY (Awaiting Vapi/Twilio Key Activation)");

        return ToolResult.builder()
                .summary("AI Voice Call strategy ready for " + student.getFullName())
                .reasoning("Tool is in SHADOW mode. Production code for Vapi.ai integration is prepared but inactive.")
                .actionData(Map.of(
                        "status", "SHADOW_SIMULATED",
                        "objective", objective,
                        "scriptVersion", "1.0-Dynamic"
                ))
                .status("SUCCESS")
                .build();
    }
}
