package com.unios.service.admissions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unios.dto.admissions.ConversationalStateDTO;
import com.unios.dto.admissions.FieldInfoDTO;
import com.unios.model.Application;
import com.unios.repository.ApplicationRepository;
import com.unios.service.agents.framework.v5.tool.impl.DocumentAnalyzerTool;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationalAdmissionService {

    private final ApplicationRepository applicationRepository;
    private final ObjectMapper objectMapper;
    private final DocumentAnalyzerTool documentAnalyzerTool;
    private JsonNode stateMachineConfig;
    
    // Per-turn validation feedback to the agent
    private final Map<Long, List<String>> turnErrors = new HashMap<>();

    @PostConstruct
    public void init() throws IOException {
        stateMachineConfig = objectMapper.readTree(new ClassPathResource("config/admission-state-machine.json").getInputStream());
    }

    public ConversationalStateDTO getCurrentState(Long applicationId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new RuntimeException("Application not found"));

        Map<String, Object> data = parseApplicationData(application.getApplicationData());
        
        // Always sync step to the first incomplete one to handle stale data
        String currentStep = syncStep(data);
        data.put("currentStep", currentStep);
        
        return buildStateDTO(applicationId.toString(), currentStep, data);
    }

    // DELETED interceptAndAutoFill: system is now strictly agentic and uses only the real LLM.

    public ConversationalStateDTO submitField(Long applicationId, String fieldId, Object value, String currentFileName) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new RuntimeException("Application not found"));

        Map<String, Object> data = parseApplicationData(application.getApplicationData());
        
        // 1. Sync step to handle out-of-order data or previous completions
        String currentStep = syncStep(data);

        if ("COMPLETED".equals(currentStep)) {
            return buildStateDTO(applicationId.toString(), currentStep, data);
        }

        turnErrors.remove(applicationId);

        // Handle common LLM hallucinations for field IDs
        String originalFieldId = fieldId;
        if ("name".equalsIgnoreCase(fieldId)) fieldId = "fullName";
        if ("emailAddress".equalsIgnoreCase(fieldId) || "emailId".equalsIgnoreCase(fieldId)) fieldId = "email";
        if ("transcript".equalsIgnoreCase(fieldId) || "document".equalsIgnoreCase(fieldId)) fieldId = "marksheet";
        if ("marks".equalsIgnoreCase(fieldId) || "score".equalsIgnoreCase(fieldId) || "totalMarks".equalsIgnoreCase(fieldId) || "school_marks".equalsIgnoreCase(fieldId) || "total_marks".equalsIgnoreCase(fieldId) || "percentage".equalsIgnoreCase(fieldId)) fieldId = "schoolMarks";
        
        if (!originalFieldId.equals(fieldId)) {
            log.info("[Conversational Service] Mapped hallucinated field '{}' to standard ID '{}'", originalFieldId, fieldId);
        }

        // 2. Find field config GLOBALLY (allow out-of-order data submission)
        JsonNode fieldConfig = null;
        for (JsonNode step : stateMachineConfig.get("steps")) {
            for (JsonNode field : step.get("fields")) {
                if (field.get("id").asText().equals(fieldId)) {
                    fieldConfig = field;
                    break;
                }
            }
            if (fieldConfig != null) break;
        }

        if (fieldConfig == null) {
            log.error("[Conversational Service] Field '{}' not recognized. Ignoring.", fieldId);
            return buildStateDTO(applicationId.toString(), currentStep, data);
        }

        try {
            validateField(fieldConfig, value);
        } catch (Exception e) {
            log.warn("[Conversational Service] Validation failed for field '{}': {}", fieldId, e.getMessage());
            turnErrors.computeIfAbsent(applicationId, k -> new ArrayList<>()).add(e.getMessage());
            return buildStateDTO(applicationId.toString(), currentStep, data);
        }

        // 3. Process file uploads
        if ("file".equals(fieldConfig.get("type").asText())) {
            if (!(value instanceof String)) {
                throw new RuntimeException("File value must be string (base64)");
            }
            String base64Content = (String) value;
            try {
                if (base64Content.contains(",")) {
                    base64Content = base64Content.split(",")[1];
                }
                byte[] pdfBytes = Base64.getDecoder().decode(base64Content);
                String uploadDir = System.getProperty("user.dir") + "/uploads/transcripts";
                File dir = new File(uploadDir);
                if (!dir.exists()) dir.mkdirs();

                String fileName = "transcript_" + applicationId + "_" + System.currentTimeMillis() + ".pdf";
                String filePath = uploadDir + "/" + fileName;
                Files.write(Paths.get(filePath), pdfBytes);

                application.setFilePath(filePath);
                log.info("[Conversational Service] Saved marksheet file to {}", filePath);

                    // --- AGENTIC UPGRADE: Trigger OCR Immediately ---
                    try {
                        log.info("[Conversational Service] Triggering OCR scan for Application {}", applicationId);
                        documentAnalyzerTool.execute(Map.of(
                            "applicationId", applicationId,
                            "filePath", filePath,
                            "fileName", currentFileName != null ? currentFileName : ""
                        ));
                        
                        // CRITICAL FIX: Reload the entity from the DB because the OCR tool modified it.
                        // This prevents line 157 from overwriting the OCR marks with null values.
                        application = applicationRepository.findById(applicationId).get();
                        
                    } catch (Exception e) {
                        log.warn("[Conversational Service] OCR scan failed: {}", e.getMessage());
                    }
            } catch (Exception e) {
                log.error("Failed to decode and save file upload", e);
                throw new RuntimeException("Invalid file format", e);
            }
        }

        // 4. Sync to legacy fields and update data map
        syncToApplicationModel(application, fieldId, value);
        data.put(fieldId, value);

        // 5. Fast-forward currentStep until we hit an incomplete step or "COMPLETED"
        currentStep = syncStep(data);
        data.put("currentStep", currentStep);

        application.setApplicationData(serializeData(data));
        applicationRepository.save(application);

        return buildStateDTO(applicationId.toString(), currentStep, data);
    }

    /**
     * Finds the first incomplete step in the workflow sequence.
     */
    private String syncStep(Map<String, Object> data) {
        for (JsonNode step : stateMachineConfig.get("steps")) {
            String stepId = step.get("id").asText();
            if (!isStepComplete(stepId, data)) {
                return stepId;
            }
        }
        return "COMPLETED";
    }

    private void syncToApplicationModel(Application app, String fieldId, Object value) {
        if ("fullName".equals(fieldId)) app.setFullName((String) value);
        if ("email".equals(fieldId)) app.setEmail((String) value);
        if ("schoolMarks".equals(fieldId)) {
            try {
                app.setSchoolMarks(Double.valueOf(value.toString()));
            } catch (Exception e) {
                log.warn("Invalid schoolMarks value: {}", value);
            }
        }
    }

    private ConversationalStateDTO buildStateDTO(String appId, String currentStep, Map<String, Object> data) {
        List<FieldInfoDTO> remainingFields = new ArrayList<>();
        
        int totalFields = 0;
        int filledFields = 0;

        for (JsonNode step : stateMachineConfig.get("steps")) {
            String stepId = step.get("id").asText();
            for (JsonNode field : step.get("fields")) {
                totalFields++;
                String fid = field.get("id").asText();
                if (isFieldFilled(fid, data)) {
                    filledFields++;
                } else if (stepId.equals(currentStep)) {
                    remainingFields.add(mapToFieldDTO(field));
                }
            }
        }

        String nextPrompt = remainingFields.isEmpty() ? 
                ("COMPLETED".equals(currentStep) ? "Your application is complete!" : "Processing...") : 
                remainingFields.get(0).getConversationalPrompt();

        Application application = applicationRepository.findById(Long.valueOf(appId))
                .orElse(null);

        if (application != null && !remainingFields.isEmpty() && "schoolMarks".equals(remainingFields.get(0).getId()) && application.getExtractedMarks() != null) {
            // Keep the prompt intact so the LLM can ask naturally using its context.
        }

        return ConversationalStateDTO.builder()
                .applicationId(appId)
                .currentStep(currentStep)
                .remainingFields(remainingFields)
                .submittedData(data)
                .progressPercentage(totalFields > 0 ? (filledFields * 100.0 / totalFields) : 0)
                .isComplete("COMPLETED".equals(currentStep))
                .nextPrompt(nextPrompt)
                .ocrVerified(application != null ? application.getOcrVerified() : null)
                .extractedMarks(application != null ? application.getExtractedMarks() : null)
                .validationErrors(turnErrors.get(Long.valueOf(appId)))
                .build();
    }

    private boolean isFieldFilled(String fieldId, Map<String, Object> data) {
        Object val = data.get(fieldId);
        if (val == null) return false;
        String s = val.toString().trim();
        if (s.isEmpty()) return false;
        
        // Strict Numeric check for marks
        if ("schoolMarks".equals(fieldId)) {
            try {
                double d = Double.parseDouble(s);
                return d > 0; // Must be a valid positive score
            } catch (Exception e) {
                return false; // Junk like "12th" fails here
            }
        }
        return true;
    }

    private JsonNode findStepConfig(String stepId) {
        for (JsonNode step : stateMachineConfig.get("steps")) {
            if (step.get("id").asText().equals(stepId)) return step;
        }
        return null;
    }

    private JsonNode findFieldConfig(String stepId, String fieldId) {
        JsonNode step = findStepConfig(stepId);
        if (step == null) return null;
        for (JsonNode field : step.get("fields")) {
            if (field.get("id").asText().equals(fieldId)) return field;
        }
        return null;
    }

    private String getNextStepId(String currentStepId) {
        JsonNode steps = stateMachineConfig.get("steps");
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).get("id").asText().equals(currentStepId)) {
                if (i + 1 < steps.size()) return steps.get(i + 1).get("id").asText();
                else return "COMPLETED";
            }
        }
        return "COMPLETED";
    }

    private boolean isStepComplete(String stepId, Map<String, Object> data) {
        JsonNode step = findStepConfig(stepId);
        if (step == null) return true;
        for (JsonNode field : step.get("fields")) {
            if (field.get("required").asBoolean() && !isFieldFilled(field.get("id").asText(), data)) {
                return false;
            }
        }
        return true;
    }

    private void validateField(JsonNode config, Object value) {
        if (config.has("validation")) {
            JsonNode v = config.get("validation");
            if (v.has("pattern")) {
                String pattern = v.get("pattern").asText();
                if (!Pattern.matches(pattern, String.valueOf(value))) {
                    throw new RuntimeException(v.has("message") ? v.get("message").asText() : "Invalid format");
                }
            }
            if (v.has("min") || v.has("max")) {
                try {
                    double val = Double.parseDouble(String.valueOf(value));
                    if (v.has("min") && val < v.get("min").asDouble()) {
                        throw new RuntimeException("Value too low. Minimum is " + v.get("min").asDouble());
                    }
                    if (v.has("max") && val > v.get("max").asDouble()) {
                        throw new RuntimeException("Value too high. Maximum is " + v.get("max").asDouble());
                    }
                } catch (NumberFormatException e) {
                    throw new RuntimeException("Please enter a valid number.");
                }
            }
        }
    }

    private FieldInfoDTO mapToFieldDTO(JsonNode f) {
        return FieldInfoDTO.builder()
                .id(f.get("id").asText())
                .label(f.get("label").asText())
                .type(f.get("type").asText())
                .required(f.get("required").asBoolean())
                .conversationalPrompt(f.get("conversationalPrompt").asText())
                .options(f.has("options") ? objectMapper.convertValue(f.get("options"), List.class) : null)
                .validation(f.has("validation") ? objectMapper.convertValue(f.get("validation"), Map.class) : null)
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseApplicationData(String json) {
        if (json == null || json.isEmpty()) return new HashMap<>();
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.error("Error parsing application data", e);
            return new HashMap<>();
        }
    }

    private String serializeData(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error serializing data", e);
        }
    }
}
