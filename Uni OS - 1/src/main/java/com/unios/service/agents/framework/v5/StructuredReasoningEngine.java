package com.unios.service.agents.framework.v5;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unios.service.agents.framework.v5.tool.Tool;
import com.unios.service.agents.framework.v5.tool.ToolRegistry;
import com.unios.service.llm.ResilientLLMService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * v5 Structured Reasoning Engine.
 * Implements strict JSON schema validation, tool registry checks, and retry mechanisms.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class StructuredReasoningEngine implements ReasoningEngine {

    private final ResilientLLMService llmService;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;

    private static final int MAX_RETRIES = 3;

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            You are the v5 Planning Core for University OS.
            Your task is to analyze the Goal, Current State, and Memory to generate a structured execution plan.

            AVAILABLE TOOLS:
            %s
            - COMPLETE: Use this tool with no parameters when the goal is fully achieved.

            OUTPUT SCHEMA REQUIREMENT:
            You MUST return ONLY a valid JSON object matching this exact structure:
            {
              "plan": [
                {
                  "step": "Brief description of the step",
                  "tool": "EXACT_TOOL_NAME",
                  "reason": "Why this step is necessary",
                  "parameters": {"key": "value"}
                }
              ]
            }

            RULES:
            1. DO NOT invent or hallucinate tools. Use ONLY the 'AVAILABLE TOOLS' listed above.
            2. DO NOT return markdown formatting (e.g., ```json). Return raw JSON only.
            3. If the goal is met, return a single step using the 'COMPLETE' tool.
            """;

    @Override
    public AgentPlan generatePlan(String goal, Map<String, Object> context, String memoryContext) {
        return executeWithRetry(goal, context, memoryContext, null);
    }

    @Override
    public AgentPlan replan(String goal, Map<String, Object> context, String memoryContext, String failureReason) {
        return executeWithRetry(goal, context, memoryContext, failureReason);
    }

    private AgentPlan executeWithRetry(String goal, Map<String, Object> context, String memoryContext, String failureReason) {
        String availableTools = toolRegistry.getRegisteredTools().stream()
                .map(t -> "- " + t.getName() + ": " + t.getDescription())
                .collect(Collectors.joining("\n"));

        String baseSystemPrompt = String.format(SYSTEM_PROMPT_TEMPLATE, availableTools);

        int attempt = 0;
        String currentFailureContext = failureReason;

        while (attempt < MAX_RETRIES) {
            attempt++;
            try {
                String inputData = buildInputContext(goal, context, memoryContext, currentFailureContext);
                
                // Fetch from LLM wrapper
                log.info("Planning Attempt {}/{}", attempt, MAX_RETRIES);
                String rawLlmResponse = llmService.executeWithResilience(baseSystemPrompt, inputData).getFullResponse();
                
                // Validate and Parse
                return validateAndMap(rawLlmResponse);

            } catch (PlanningValidationException e) {
                log.warn("Planning Validation Failed on attempt {}: {}", attempt, e.getMessage());
                currentFailureContext = "PREVIOUS OUTPUT WAS INVALID. FIX THIS ERROR: " + e.getMessage();
            } catch (Exception e) {
                log.error("Critical LLM parsing failure on attempt {}: {}", attempt, e.getMessage());
                currentFailureContext = "CRITICAL JSON ERROR. RETURN ONLY VALID JSON.";
            }
        }

        log.error("Failed to generate a valid plan after {} retries.", MAX_RETRIES);
        throw new IllegalStateException("LLM failed to generate valid structured plan after max retries.");
    }

    private AgentPlan validateAndMap(String rawResponse) throws JsonProcessingException {
        // Strip markdown if LLM disobeyed
        String cleanedJson = rawResponse.replaceAll("^```json\\s*", "").replaceAll("```$", "").trim();

        JsonNode rootNode = objectMapper.readTree(cleanedJson);

        if (!rootNode.has("plan") || !rootNode.get("plan").isArray() || rootNode.get("plan").isEmpty()) {
            throw new PlanningValidationException("Schema violation: Missing or empty 'plan' array.");
        }

        JsonNode firstStep = rootNode.get("plan").get(0);

        if (!firstStep.has("tool") || !firstStep.has("reason")) {
            throw new PlanningValidationException("Schema violation: Step must contain 'tool' and 'reason'.");
        }

        String toolName = firstStep.get("tool").asText();
        String reason = firstStep.get("reason").asText();
        
        JsonNode paramsNode = firstStep.get("parameters");
        Map<String, Object> parameters = paramsNode != null && !paramsNode.isNull() 
                ? objectMapper.convertValue(paramsNode, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}) 
                : Map.of();

        // Validate complete
        if ("COMPLETE".equalsIgnoreCase(toolName)) {
            return AgentPlan.builder()
                    .toolName(null)
                    .parameters(Map.of())
                    .reasoning(reason)
                    .complete(true)
                    .build();
        }

        // Validate tool against registry
        Set<String> validToolNames = toolRegistry.getRegisteredTools().stream().map(Tool::getName).collect(Collectors.toSet());
        if (!validToolNames.contains(toolName)) {
            throw new PlanningValidationException("Hallucination detected: Tool '" + toolName + "' does not exist in registry.");
        }

        return AgentPlan.builder()
                .toolName(toolName)
                .parameters(parameters)
                .reasoning(reason)
                .complete(false)
                .build();
    }

    private String buildInputContext(String goal, Map<String, Object> context, String memoryContext, String failureReason) {
        StringBuilder sb = new StringBuilder();
        sb.append("GOAL: ").append(goal).append("\n\n");
        sb.append("CURRENT STATE: \n").append(context).append("\n\n");
        sb.append("MEMORY (Trace): \n").append(memoryContext).append("\n\n");
        
        if (failureReason != null) {
            sb.append("!!! CRITICAL FEEDBACK FROM LAST ATTEMPT !!!\n").append(failureReason).append("\n\n");
        }
        
        return sb.toString();
    }

    private static class PlanningValidationException extends RuntimeException {
        public PlanningValidationException(String message) {
            super(message);
        }
    }
}
