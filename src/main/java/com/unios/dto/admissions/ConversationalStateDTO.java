package com.unios.dto.admissions;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationalStateDTO {
    private String applicationId;
    private String currentStep;
    private List<FieldInfoDTO> remainingFields;
    private Map<String, Object> submittedData;
    private double progressPercentage;
    private boolean isComplete;
    private String nextPrompt;
    
    // Scan context for agentic prompts
    private Boolean ocrVerified;
    private String extractedMarks;
    
    // Feedback for the agent to use in corrections
    private List<String> validationErrors;
}
