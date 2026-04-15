package com.unios.service.agents.admissions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unios.dto.admissions.ConversationalStateDTO;
import com.unios.dto.admissions.FieldInfoDTO;
import com.unios.service.admissions.ConversationalAdmissionService;
import com.unios.service.admissions.EligibilityEngineService;
import com.unios.service.llm.LLMClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ChatAdmissionAgent {

    private final LLMClient llmClient;
    private final ConversationalAdmissionService stateService;
    private final EligibilityEngineService eligibilityEngine;
    private final ObjectMapper objectMapper;
    private String universityPolicy;

    public ChatAdmissionAgent(
            @org.springframework.beans.factory.annotation.Qualifier("ollamaClient") LLMClient llmClient,
            ConversationalAdmissionService stateService,
            EligibilityEngineService eligibilityEngine,
            ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.stateService = stateService;
        this.eligibilityEngine = eligibilityEngine;
        this.objectMapper = objectMapper;
        loadUniversityPolicy();
    }

    private void loadUniversityPolicy() {
        try {
            org.springframework.core.io.ClassPathResource resource =
                new org.springframework.core.io.ClassPathResource("config/university-policy.json");
            this.universityPolicy = new String(resource.getInputStream().readAllBytes());
            log.info("[AGENT] Loaded University Policy Knowledge Base.");
        } catch (Exception e) {
            log.error("[AGENT] Failed to load University Policy: {}", e.getMessage());
            this.universityPolicy = "General University Admission Policy applies.";
        }
    }

    public ChatResult processMessage(Long applicationId, String userMessage) {
        log.info("[AGENT] Processing message for App {}: {}", applicationId, userMessage);

        // Safe from fake intercepts - processing natively using LLM

        // 1. Get current state (now with data if intercepted)
        ConversationalStateDTO state = stateService.getCurrentState(applicationId);

        // 2. Perform Manual Fallback Extraction IMMEDIATELY (before LLM call)
        // This ensures your Name, Email, and Marks are saved even if the LLM is offline.
        runManualExtraction(applicationId, state, userMessage);
        
        // Refresh state after manual submission to get the latest progress
        state = stateService.getCurrentState(applicationId);

        // ==========================================================
        // *** DETERMINISTIC INTERCEPT (NO LLM NEEDED) ***
        // If OCR marks exist and schoolMarks not yet confirmed => 
        // answer instantly, bypassing the unreliable LLM tunnel.
        // ==========================================================
        if (state.getExtractedMarks() != null
                && !state.getExtractedMarks().isEmpty()
                && !state.getSubmittedData().containsKey("schoolMarks")) {
            try {
                JsonNode marksNode = objectMapper.readTree(state.getExtractedMarks());
                if (marksNode.has("marks")) {
                    double numericMarks = marksNode.get("marks").asDouble();
                    if (numericMarks > 0) {
                        String deterministicReply;
                        if (Boolean.TRUE.equals(state.getOcrVerified())) {
                            deterministicReply = "✅ I've verified your marksheet and recorded your total as **"
                                    + (int) numericMarks + " marks** out of 600. Is this correct? "
                                    + "If yes, just reply \"Yes\" and I'll proceed with your eligibility check.";
                        } else {
                            deterministicReply = "📄 The document scan was a bit fuzzy, but I've estimated your total marks at around **"
                                    + (int) numericMarks + "**. Could you please confirm the exact total from your marksheet?";
                        }
                        log.info("[AGENT] *** DETERMINISTIC INTERCEPT FIRED: {} marks, golden={} ***",
                                numericMarks, Boolean.TRUE.equals(state.getOcrVerified()));
                        return new ChatResult(deterministicReply, state.getProgressPercentage(), false, null);
                    }
                }
            } catch (Exception e) {
                log.warn("[AGENT] Intercept parse failed, falling back to LLM: {}", e.getMessage());
            }
        }

        try {
            // 3. Build the agentic prompt based on current application state
            String systemPrompt = buildExtractionPrompt(state);

            // 4. Call LLM
            String rawResponse = llmClient.generateResponse(systemPrompt, userMessage);
            log.info("[AGENT] Raw response: {}", rawResponse);

            // 5. Robust JSON Extraction and Fallback Parser
            String jsonContent = "{}";
            String llmAcknowledgment = "";
            try {
                // Try to find raw JSON block first
                int start = rawResponse.indexOf("{");
                int end = rawResponse.lastIndexOf("}");
                if (start != -1 && end != -1 && end > start) {
                    jsonContent = rawResponse.substring(start, end + 1);
                } else {
                    // Fallback! 8B models might fail to output JSON brackets. Create dummy JSON.
                    jsonContent = "{\"reply\": \"I'm processing that for you...\", \"extractedFields\": {}}";
                }
                
                // Further clean markdown block wrappers if present (e.g., ```json ... ```)
                if (jsonContent.contains("```json")) {
                    jsonContent = jsonContent.replaceAll("(?s)```json\\s*", "");
                }
                if (jsonContent.contains("```")) {
                    jsonContent = jsonContent.replaceAll("(?s)```\\s*", "");
                }
                
                JsonNode root = objectMapper.readTree(jsonContent);

                // 6. Extract the acknowledgment
                if (root.has("reply")) {
                    llmAcknowledgment = root.get("reply").asText();
                } else if (root.has("response")) {
                    llmAcknowledgment = root.get("response").asText();
                }

                // 7. Submit LLM-extracted fields
                JsonNode fieldsNode = root.has("extractedFields") ? root.get("extractedFields") :
                                      (root.has("extracted_fields") ? root.get("extracted_fields") : null);

                if (fieldsNode != null && fieldsNode.isObject()) {
                    Iterator<Map.Entry<String, JsonNode>> fields = fieldsNode.fields();
                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> field = fields.next();
                        String fieldId = field.getKey();
                        try {
                            Object value = field.getValue().isNumber()
                                    ? field.getValue().asDouble()
                                    : field.getValue().asText();
                            log.info("[AGENT] Submitting LLM field {}: {}", fieldId, value);
                            stateService.submitField(applicationId, fieldId, value, null);
                        } catch (Exception e) {
                            log.warn("[AGENT] Could not submit field {}: {}", fieldId, e.getMessage());
                        }
                    }
                }
                
                // Simple fallback for LLM response if reply is completely empty
                if (llmAcknowledgment.trim().isEmpty() && rawResponse.length() > 5) {
                    llmAcknowledgment = rawResponse.split("\\{")[0].trim();
                }
            } catch (Exception parseException) {
                log.warn("[AGENT] Failed to parse internal JSON block: {}", parseException.getMessage());
            }

            // 8. Get UPDATED state after submissions
            ConversationalStateDTO updatedState = stateService.getCurrentState(applicationId);

            // 9. Build the final reply:
            String finalReply;
            com.unios.dto.admissions.EligibilityResult eligibility = null;

            if (updatedState.isComplete()) {
                eligibility = eligibilityEngine.evaluate(applicationId);
                // If it was already complete BEFORE this turn, the user is likely asking a follow-up.
                // We should prioritize the LLM's answer.
                if (state.isComplete() && llmAcknowledgment != null && !llmAcknowledgment.trim().isEmpty()) {
                    finalReply = llmAcknowledgment;
                } else if (!state.isComplete()) {
                    // Just became complete this turn! Show the requested hardcoded success message.
                    finalReply = "I have got all the information needed. I have completed the application for you, and you will receive the admission status in your email address shortly.";
                } else {
                    // Already complete but LLM failed to give a follow-up, show default success
                    finalReply = "Your application is complete and you will receive the admission status in your email address shortly.";
                }
            } else {
                // Agentic Logic: If fields were successfully extracted, override the AI's hallucinated questions
                // and use the rigid State Machine to ask exactly what is missing next.
                int startRemaining = state.getRemainingFields().size();
                int endRemaining = updatedState.getRemainingFields().size();

                if (llmAcknowledgment != null && !llmAcknowledgment.trim().isEmpty() && llmAcknowledgment.length() > 5) {
                     // If the LLM gave a meaningful reply (e.g. 'I found 471 marks'), PRIORITIZE IT.
                     finalReply = llmAcknowledgment;
                } else if (endRemaining < startRemaining && updatedState.getNextPrompt() != null) {
                     // Fallback to state machine only if the AI was silent or too brief.
                     finalReply = "Got it! " + updatedState.getNextPrompt();
                } else {
                     finalReply = updatedState.getNextPrompt() != null ? updatedState.getNextPrompt() : "Could you provide more details?";
                }
            }

            return new ChatResult(
                    finalReply,
                    updatedState.getProgressPercentage(),
                    updatedState.isComplete(),
                    eligibility
            );

        } catch (Exception e) {
            log.warn("[AGENT] LLM Offline or Error (Tunnel Dead?): {}. Using Deterministic UI Fallback.", e.getMessage());
            
            // RELOAD STATE (ensures we see the OCR results even on error)
            ConversationalStateDTO updatedState = stateService.getCurrentState(applicationId);
            
            String finalReply = updatedState.getNextPrompt();
            
            // --- ULTIMATE DEMO SAFETY NET ---
            // If LLM is offline, look for OCR results and act like the AI.
            if (updatedState.getExtractedMarks() != null && !updatedState.getExtractedMarks().isEmpty() && !updatedState.getSubmittedData().containsKey("schoolMarks")) {
                try {
                    JsonNode marksNode = objectMapper.readTree(updatedState.getExtractedMarks());
                    if (marksNode.has("marks")) {
                        double numericMarks = marksNode.get("marks").asDouble();
                        if (numericMarks > 0) {
                            if (Boolean.TRUE.equals(updatedState.getOcrVerified())) {
                                finalReply = "Verified! I've scanned your marksheet and found " + numericMarks + " marks. Is this correct?";
                            } else {
                                finalReply = "The scan was a bit fuzzy, but I've estimated your marks at " + numericMarks + ". Could you please confirm the exact total?";
                            }
                        }
                    }
                } catch (Exception parseError) {
                    log.error("[AGENT] Fallback parse error: {}", parseError.getMessage());
                }
            }

            if (updatedState.isComplete()) {
                finalReply = "Thank you! Your application is complete and you will receive the admission status in your email address shortly.";
            }

            return new ChatResult(
                    finalReply, 
                    updatedState.getProgressPercentage(), 
                    updatedState.isComplete(), 
                    updatedState.isComplete() ? eligibilityEngine.evaluate(applicationId) : null
            );
        }
    }

    private void runManualExtraction(Long applicationId, ConversationalStateDTO state, String userMessage) {
        String lowerText = userMessage.toLowerCase();
        
        // 1. Name Extraction
        java.util.regex.Matcher mName = java.util.regex.Pattern.compile("(?i)(?:i am|my name is|this is|i'm)\\s+([A-Za-z]+(?:\\s+[A-Za-z]+){0,2})").matcher(userMessage);
        if (mName.find()) {
            stateService.submitField(applicationId, "fullName", mName.group(1).trim(), null);
        } else if (!state.getRemainingFields().isEmpty() && "fullName".equals(state.getRemainingFields().get(0).getId())) {
            // If we are currently ASKING for a name, treat short alphabetic inputs as names
            String cleanMsg = userMessage.replaceAll("[^a-zA-Z\\s]", "").trim();
            if (cleanMsg.length() >= 3 && cleanMsg.length() <= 30 && cleanMsg.split("\\s+").length <= 3) {
                if (!cleanMsg.toLowerCase().contains("upload") && !cleanMsg.toLowerCase().contains("pdf") && !cleanMsg.toLowerCase().contains("score")) {
                    stateService.submitField(applicationId, "fullName", cleanMsg, null);
                }
            }
        }

        // 2. Email Extraction
        java.util.regex.Matcher mEmail = java.util.regex.Pattern.compile("([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6})").matcher(userMessage);
        if (mEmail.find()) stateService.submitField(applicationId, "email", mEmail.group(1), null);

        // 3. Score Extraction
        java.util.regex.Matcher mMarks = java.util.regex.Pattern.compile("(?:score|marks|scored)\\s+(?:of|is|are)?\\s*(\\d{2,3}(?:\\.\\d+)?)").matcher(lowerText);
        if (mMarks.find()) {
            stateService.submitField(applicationId, "schoolMarks", Double.parseDouble(mMarks.group(1)), null);
        } else if (state.getExtractedMarks() != null && !state.getSubmittedData().containsKey("schoolMarks")) {
            // If OCR marks exist and we are waiting for confirmation, accept "yes", "correct", etc.
            if (lowerText.contains("yes") || lowerText.contains("correct") || lowerText.contains("yeah") || lowerText.contains("yep")) {
                try {
                    JsonNode marksNode = objectMapper.readTree(state.getExtractedMarks());
                    if (marksNode.has("marks")) {
                        double numericMarks = marksNode.get("marks").asDouble();
                        if (numericMarks > 0) {
                            stateService.submitField(applicationId, "schoolMarks", numericMarks, null);
                        }
                    }
                } catch (Exception e) {
                    log.error("[AGENT] Failed to auto-confirm marks", e);
                }
            } else {
                // If they just typed a raw number like "304"
                java.util.regex.Matcher mRawMarks = java.util.regex.Pattern.compile("\\b(\\d{2,3}(?:\\.\\d+)?)\\b").matcher(lowerText);
                if (mRawMarks.find()) {
                    stateService.submitField(applicationId, "schoolMarks", Double.parseDouble(mRawMarks.group(1)), null);
                }
            }
        }
    }

    private String buildExtractionPrompt(ConversationalStateDTO state) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are the University Admission Assistant.\n\n");
        sb.append("KNOWLEDGE BASE (For answering user questions):\n");
        sb.append(this.universityPolicy).append("\n\n");
        sb.append("CURRENT APPLICATION DATA:\n");
        sb.append(state.getSubmittedData()).append("\n\n");

        sb.append("YOUR TASK:\n");
        sb.append("Extract any newly provided fields from the user's message, and generate a conversational reply. If the user asks a question, answer it using the KNOWLEDGE BASE.\n\n");

        if (state.getValidationErrors() != null && !state.getValidationErrors().isEmpty()) {
            sb.append("ERRORS:\n").append(state.getValidationErrors()).append(". Ask the user to correct them gently.\n\n");
        }

        List<FieldInfoDTO> remainingFields = state.getRemainingFields();
        if (remainingFields.isEmpty()) {
            sb.append("No required fields remain. Your primary task is to answer whatever question the user just asked using the Knowledge Base.\n");
        } else {
            sb.append("MISSING REQUIRED FIELDS:\n");
            for (FieldInfoDTO f : remainingFields) {
                sb.append("- ").append(f.getId()).append(" (Ask: ").append(f.getConversationalPrompt()).append(")\n");
            }
        }

        sb.append("\nRULES:\n");
        sb.append("1. Extract fields from the user's message. CRITICAL: Do NOT ask for a field if the user just provided it in their message!\n");
        sb.append("2. Be brief and conversational. Ask ONLY for fields that are STILL MISSING. If you extracted a field, DO NOT ask for it in the reply.\n");
        sb.append("### SYSTEM RULES: DO NOT BREAK CHARACTER ###\n");
        sb.append("- You are the 'UniOS Automated Admission Gate'.\n");
        sb.append("- You MUST ONLY output valid JSON. No conversational filler, no 'Sure, here is...', no conversational prefixes.\n");
        sb.append("- You must behave like a strict, professional admission officer.\n");
        sb.append("- If a field is missing, ask for it politely but firmly.\n");
        sb.append("- If you see 'HEURISTIC MARK VERIFICATION' below, you MUST perform the Verification Protocol first.\n");
        
        sb.append("\n### DATA EXTRACTION GOALS ###\n");
        sb.append("1. Extract candidate's full name to 'fullName'.\n");
        sb.append("2. Extract candidate's email to 'email'.\n");
        sb.append("3. Verify if a marksheet PDF/Image was uploaded (filePath exists).\n");
        sb.append("4. Extract 12th marks to 'schoolMarks' as a number.\n");
        
        if (state.getExtractedMarks() != null && !state.getExtractedMarks().isEmpty() && !state.getSubmittedData().containsKey("schoolMarks")) {
            Double numericMarks = null;
            try {
                JsonNode marksNode = objectMapper.readTree(state.getExtractedMarks());
                if (marksNode.has("marks")) {
                    numericMarks = marksNode.get("marks").asDouble();
                }
            } catch (Exception e) {
                log.warn("[AGENT] Could not parse extracted marks for prompt: {}", e.getMessage());
            }

            if (numericMarks != null && numericMarks > 0) {
                sb.append("\n### STRICT VERIFICATION PROTOCOL (DO NOT DEVIATE) ###\n");
                sb.append("The document scan finished with result: ").append(numericMarks).append(" marks. Quality: ").append(state.getOcrVerified() ? "VERIFIED" : "ESTIMATED").append("\n");
                sb.append("YOUR RESPONSE MUST START WITH THIS EXACT TEMPLATE:\n");
                if (state.getOcrVerified()) {
                    sb.append("\"I've verified your marksheet and recorded your total as ").append(numericMarks).append(" marks. Is this correct?\"\n");
                } else {
                    sb.append("\"The document scan was a bit fuzzy, but I've estimated your total marks at around ").append(numericMarks).append(". Could you please confirm the exact total from your marksheet?\"\n");
                }
                sb.append("DO NOT ASK FOR THE NEXT FIELD until the user confirms the number.\n");
            } else {
                sb.append("\n### FALLBACK: MANUAL ENTRY NEEDED ###\n");
                sb.append("Say: 'I received your document but the text is a bit unclear. To be safe, could you please tell me your exact total marks out of 600?'\n");
            }
        } else if (!state.getSubmittedData().containsKey("schoolMarks")) {
            sb.append("\n*** MANUAL MARK ENTRY (OCR FAILED) ***\n");
            sb.append("The user is providing their marks. Look for numbers like '471', '405', etc.\n");
            sb.append("If they say '471 out of 600', extract 471. Add it to 'schoolMarks' in extractedFields.\n");
        }
        
        sb.append("ALWAYS MUST Output ONLY a JSON object and nothing else.\n\n");

        sb.append("JSON FORMAT:\n");
        sb.append("{\n");
        sb.append("  \"reply\": \"(your conversational reply asking for the next missing field)\",\n");
        sb.append("  \"extractedFields\": {\n");
        sb.append("    \"(fieldId)\": \"(extracted value)\"\n");
        sb.append("  }\n");
        sb.append("}\n");

        return sb.toString();
    }

    /**
     * Strips trailing questions the LLM may have generated (we don't want double questions).
     */
    private String stripTrailingQuestion(String text) {
        // If the LLM already added a question mark sentence at the end, remove it
        String[] sentences = text.split("(?<=[.!?])\\s+");
        StringBuilder result = new StringBuilder();
        for (String sentence : sentences) {
            if (!sentence.trim().endsWith("?")) {
                if (result.length() > 0) result.append(" ");
                result.append(sentence.trim());
            }
        }
        return result.length() > 0 ? result.toString() : text;
    }

    // Inner classes
    @lombok.Data
    @lombok.NoArgsConstructor
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public static class LlmExtractionResult {
        @com.fasterxml.jackson.annotation.JsonAlias({"reply", "Reply", "message", "response"})
        private String reply;

        @com.fasterxml.jackson.annotation.JsonAlias({"extractedFields", "extracted_fields", "fields", "ExtractedFields"})
        private Map<String, Object> extractedFields;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class ChatResult {
        private String reply;
        private double progress;
        private boolean isComplete;
        private com.unios.dto.admissions.EligibilityResult eligibility;
    }
}
