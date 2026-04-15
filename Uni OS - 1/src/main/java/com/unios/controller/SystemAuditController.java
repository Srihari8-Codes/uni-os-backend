package com.unios.controller;

import com.unios.model.Application;
import com.unios.model.ParentNotification;
import com.unios.repository.ApplicationRepository;
import com.unios.repository.ParentNotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SystemAuditController (v4.1) 
 * 
 * Provides visibility into the Agentic OS's multi-modal results.
 * Access this to see OCR extractions and parent interactions.
 */
@RestController
@RequestMapping("/api/audit/system")
@RequiredArgsConstructor
public class SystemAuditController {

    private final ApplicationRepository applicationRepository;
    private final ParentNotificationRepository notificationRepository;

    @GetMapping("/ocr-results")
    public ResponseEntity<?> getOcrResults() {
        List<Application> apps = applicationRepository.findAll().stream()
                .filter(a -> a.getOcrTranscript() != null)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(apps.stream().map(a -> Map.of(
                "applicationId", a.getId(),
                "name", a.getFullName(),
                "reportedScore", a.getAcademicScore(),
                "ocrScore", a.getOcrAcademicScore() != null ? a.getOcrAcademicScore() : "PENDING",
                "verified", a.getOcrVerified() != null ? a.getOcrVerified() : "N/A",
                "auditLog", a.getOcrAuditLog() != null ? a.getOcrAuditLog() : ""
        )).collect(Collectors.toList()));
    }

    @GetMapping("/parent-interactions")
    public ResponseEntity<?> getParentInteractions() {
        List<ParentNotification> notifications = notificationRepository.findAll();
        
        return ResponseEntity.ok(notifications.stream().map(n -> Map.of(
                "studentName", n.getStudent().getFullName(),
                "type", n.getType(),
                "aiMessage", n.getAiMessage(),
                "parentReply", n.getParentReply() != null ? n.getParentReply() : "AWAITING_REPLY",
                "rootCause", n.getRootCause() != null ? n.getRootCause() : "N/A",
                "sentiment", n.getSentimentScore() != null ? n.getSentimentScore() : 0.0,
                "sentAt", n.getSentAt()
        )).collect(Collectors.toList()));
    }
}
