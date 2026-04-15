package com.unios.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DecisionResponse {
    private String action;
    private String reasoning;
    private Map<String, Object> parameters;
    private Double confidence;

    // --- Traceability ---
    private String fullPrompt;
    private String fullResponse;

    /**
     * Manual constructor for fallback scenarios.
     */
    public DecisionResponse(String action, String reasoning, Map<String, Object> parameters, Double confidence) {
        this.action = action;
        this.reasoning = reasoning;
        this.parameters = parameters;
        this.confidence = confidence;
    }
}
