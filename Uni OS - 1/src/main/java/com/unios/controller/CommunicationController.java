package com.unios.controller;

import com.unios.model.ParentNotification;
import com.unios.repository.ParentNotificationRepository;
import com.unios.service.event.UniversityEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * CommunicationController (v4.1) — Simulation of Inbound SMS/Email Webhooks.
 * 
 * In production, this would be called by Twilio or SendGrid.
 * It simulates the "Parent Reply" loop where voice/text is processed.
 */
@RestController
@RequestMapping("/api/webhooks/comm")
@Slf4j
@RequiredArgsConstructor
public class CommunicationController {

    private final ParentNotificationRepository notificationRepository;
    private final UniversityEventPublisher eventPublisher;

    /**
     * POST /api/webhooks/comm/reply
     * Simulate a parent replying to an SMS/Email.
     */
    @PostMapping("/reply")
    public ResponseEntity<?> handleParentReply(@RequestBody Map<String, Object> payload) {
        Long notificationId = Long.valueOf(payload.get("notificationId").toString());
        String replyText = (String) payload.get("reply");
        
        ParentNotification notification = notificationRepository.findById(notificationId).orElse(null);
        if (notification == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Notification not found"));
        }

        log.info("[WEBHOOK] Received parent reply for Notification {}: '{}'", notificationId, replyText);

        // 1. Store the reply
        notification.setParentReply(replyText);
        
        // 2. Mock Sentiment Extraction (Production would use an LLM utility)
        if (replyText.toLowerCase().contains("hospital") || replyText.toLowerCase().contains("sick")) {
            notification.setRootCause("SICKNESS");
            notification.setSentimentScore(0.8); // Cooperative/Informative
        } else if (replyText.toLowerCase().contains("wrong") || replyText.toLowerCase().contains("stop")) {
            notification.setRootCause("COMPLAINT");
            notification.setSentimentScore(0.2); // Frustrated
        } else {
            notification.setRootCause("OTHER");
            notification.setSentimentScore(0.5);
        }

        notificationRepository.save(notification);

        // 3. Trigger "Reflection" cycle — the system now knows the reason.
        // In a real system, this would fire an event that causes the AttendanceGuardian
        // to re-evaluate the student's risk and update the StateBoard.
        log.info("[WEBHOOK] Reflection data stored. Reason: {}, Sentiment: {}", 
                 notification.getRootCause(), notification.getSentimentScore());

        return ResponseEntity.ok(Map.of(
                "status", "PROCESSED",
                "extractedReason", notification.getRootCause(),
                "sentiment", notification.getSentimentScore()
        ));
    }
}
