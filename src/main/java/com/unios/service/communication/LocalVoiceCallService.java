package com.unios.service.communication;

import com.unios.model.Student;
import com.unios.repository.StudentRepository;
import com.unios.service.llm.OllamaClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * LocalVoiceCallService - [INACTIVE / DEMO]
 * 
 * This service implements autonomous parent calling using a Local LLM (Ollama) 
 * paired with a local SIP/WebRTC gateway (e.g., Asterisk or FreePBX).
 * 
 * DEPLOYMENT NOTE: Currently disabled in favor of RetellCallService (Cloud) 
 * to ensure 100% uptime for the final pilot, but remains as the 'Privacy-First' 
 * local execution fallback.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LocalVoiceCallService {

    private final StudentRepository studentRepository;
    private final OllamaClient ollamaClient;

    /**
     * Simulation of a Local Call using Ollama for speech-to-text-to-speech loop.
     */
    public void callParentLocally(Long studentId, String subject) {
        Student student = studentRepository.findById(studentId).orElse(null);
        if (student == null) return;

        log.info("[LOCAL VOICE] Initializing Private Phone Bridge for: {}", student.getFullName());
        
        // 1. Generate Voice Persona via Local LLM
        String scriptPrompt = String.format(
            "You are a local university assistant. Call the parent of %s regarding %s. " +
            "If they answer, ask for the reason for absence. Keep it under 30 seconds.",
            student.getFullName(), subject
        );

        log.info("[LOCAL VOICE] Generating script via Ollama (llama3.2)...");
        // String script = ollamaClient.generate(scriptPrompt); 

        // 2. Handshake with Local Telephony Gateway (Stubbed for Demo)
        log.info("[LOCAL VOICE] Connecting to Local SIP Gateway at 127.0.0.1:5060...");
        
        System.out.println(">>> [LOCAL VOICE SIMULATION] Connecting to parent: " + student.getParentPhone());
        System.out.println(">>> [LOCAL VOICE SIMULATION] Status: STANDBY (Hardware interface required)");
    }
}
