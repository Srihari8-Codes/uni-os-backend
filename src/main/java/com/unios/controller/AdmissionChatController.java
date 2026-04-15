package com.unios.controller;

import com.unios.service.agents.admissions.ChatAdmissionAgent;
import com.unios.service.agents.admissions.ChatAdmissionAgent.ChatResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admission-chat")
@RequiredArgsConstructor
@CrossOrigin
public class AdmissionChatController {

    private final ChatAdmissionAgent chatAgent;

    /**
     * User sends a message to the AI admission officer.
     */
    @PostMapping("/{applicationId}")
    public ResponseEntity<ChatResult> chat(
            @PathVariable Long applicationId,
            @RequestBody Map<String, String> body) {
        
        String userMessage = body.get("message");
        if (userMessage == null || userMessage.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        ChatResult result = chatAgent.processMessage(applicationId, userMessage);
        return ResponseEntity.ok(result);
    }
}
