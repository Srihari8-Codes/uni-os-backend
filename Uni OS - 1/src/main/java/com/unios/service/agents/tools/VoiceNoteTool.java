package com.unios.service.agents.tools;

import com.unios.model.ParentNotification;
import com.unios.model.Student;
import com.unios.repository.ParentNotificationRepository;
import com.unios.repository.StudentRepository;
import com.unios.service.agents.framework.AgentTool;
import com.unios.service.agents.framework.ToolContext;
import com.unios.service.agents.framework.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * VoiceNoteTool — The "Voice" of the Attendance Guardian.
 * 
 * In production, this would call a TTS API (OpenAI/Google).
 * For now, it generates the reasoning text and "simulates" 
 * sending an audio file via SMS/Email.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class VoiceNoteTool implements AgentTool {

    private final StudentRepository studentRepository;
    private final ParentNotificationRepository notificationRepository;

    @Override
    public String name() {
        return "SEND_VOICE_NOTE";
    }

    @Override
    public String description() {
        return "Generates a personalized voice note (TTS) and sends it via SMS/Email. " +
               "Input: {studentId: 1, message: 'Your reasoning here'}";
    }

    @Override
    public ToolResult executeWithContext(ToolContext context) {
        Long studentId = ((Number) context.getParameters().get("studentId")).longValue();
        String message = (String) context.getParameters().get("message");

        Student student = studentRepository.findById(studentId).orElse(null);
        if (student == null) {
            return ToolResult.builder().summary("Student not found").status("FAILED").build();
        }

        log.info("[VOICE NOTE] Generating audio for: {} (Message: '{}')", student.getFullName(), message);

        // --- PRODUCTION SIMULATION ---
        // 1. Convert text to speech (Mocked)
        String audioFileUrl = "https://cdn.unios.edu/audio/notifications/" + System.currentTimeMillis() + ".ogg";
        
        // 2. Track in DB
        ParentNotification notification = new ParentNotification();
        notification.setStudent(student);
        notification.setParentEmail(student.getParentEmail());
        notification.setType("VOICE_ALERT");
        notification.setAiMessage("AUDIO_LINK: " + audioFileUrl + " | TRANSCRIPT: " + message);
        notification.setSentAt(LocalDateTime.now());
        notificationRepository.save(notification);

        return ToolResult.builder()
                .summary("Voice note sent via SMS and Email to " + student.getParentEmail())
                .reasoning("Personalized audio generated using TTS from provided message.")
                .actionData(Map.of("channel", "MULTI_CHANNEL", "audioUrl", audioFileUrl))
                .status("SUCCESS")
                .build();
    }
}
