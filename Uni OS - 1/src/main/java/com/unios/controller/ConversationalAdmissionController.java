package com.unios.controller;

import com.unios.dto.admissions.ConversationalStateDTO;
import com.unios.service.admissions.ConversationalAdmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/conversational-admissions")
@RequiredArgsConstructor
@CrossOrigin
public class ConversationalAdmissionController {

    private final ConversationalAdmissionService conversationalAdmissionService;

    /**
     * Get the current state of a conversational admission process.
     * @param id The application ID
     * @return The current state, including remaining fields and next prompt.
     */
    @GetMapping("/{id}/state")
    public ConversationalStateDTO getState(@PathVariable Long id) {
        return conversationalAdmissionService.getCurrentState(id);
    }

    /**
     * Submit a single field value to advance the state machine.
     * @param id The application ID
     * @param payload Map containing 'fieldId' and 'value'
     * @return The updated state after processing the field.
     */
    @PostMapping("/{id}/submit-field")
    public ConversationalStateDTO submitField(
            @PathVariable Long id,
            @RequestBody Map<String, Object> payload) {
        String fieldId = (String) payload.get("fieldId");
        Object value = payload.get("value");
        String fileName = (String) payload.getOrDefault("fileName", "");
        
        if (fieldId == null) {
            throw new IllegalArgumentException("fieldId is required");
        }
        
        return conversationalAdmissionService.submitField(id, fieldId, value, fileName);
    }
}
