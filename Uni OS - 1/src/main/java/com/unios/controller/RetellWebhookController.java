package com.unios.controller;

import com.unios.model.AbsenceReason;
import com.unios.repository.AbsenceReasonRepository;
import com.unios.service.llm.ResilientLLMService;
import com.unios.dto.DecisionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping
@Slf4j
@RequiredArgsConstructor
public class RetellWebhookController {

    private final AbsenceReasonRepository absenceReasonRepository;
    private final ResilientLLMService llmService;

    @PostMapping({"/retell/webhook", "/api/retell-webhook"})
    public ResponseEntity<?> handleRetellWebhook(@RequestBody Map<String, Object> payload) {
        Object event = payload.get("event");
        log.info("[RETELL WEBHOOK] Event Received: {}", event);
        System.out.println("[RETELL WEBHOOK] DEBUG: Received event type: " + event);
        
        if ("call_started".equals(event)) {
            log.info("[RETELL WEBHOOK] Call started for student ID from metadata.");
            return ResponseEntity.ok().build();
        }
        
        if ("call_ended".equals(event)) {
            log.info("[RETELL WEBHOOK] Call ended. Waiting for analysis...");
            return ResponseEntity.ok().build();
        }

        if (!"call_analyzed".equals(event)) {
            return ResponseEntity.ok().build();
        }
        
        log.info("[RETELL WEBHOOK] Proceeding with analysis extraction...");

        try {
            Map<String, Object> call = (Map<String, Object>) payload.get("call");
            Map<String, Object> metadata = (Map<String, Object>) call.get("metadata");
            
            String transcript = (String) call.get("transcript");
            Long studentId = Long.valueOf(metadata.get("student_id").toString());
            String studentName = (String) metadata.get("student_name");
            String subjectName = (String) metadata.get("subject_name");

            log.info("[RETELL WEBHOOK] Extracting reason for Student {}: {}", studentName, transcript.substring(0, Math.min(transcript.length(), 100)));

            // Extract reason using LLM
            String systemPrompt = "You are an AI assistant for a university. Analyze the following transcript of a call with a parent about their student's absence. Extract the EXACT REASON for absence in 1 concise sentence.";
            String userPrompt = "Transcript: " + transcript;

            DecisionResponse response = llmService.executeWithResilience(systemPrompt, userPrompt);
            String reason = response.getReasoning();

            AbsenceReason ar = AbsenceReason.builder()
                    .studentId(studentId)
                    .studentName(studentName)
                    .subjectName(subjectName)
                    .timestamp(LocalDateTime.now())
                    .reason(reason)
                    .source("AI_CALL")
                    .build();

            absenceReasonRepository.save(ar);
            log.info("[RETELL WEBHOOK] Stored absence reason for student {}: {}", studentName, reason);

        } catch (Exception e) {
            log.error("[RETELL WEBHOOK] Error processing webhook: {}", e.getMessage());
        }

        return ResponseEntity.ok().build();
    }
}
