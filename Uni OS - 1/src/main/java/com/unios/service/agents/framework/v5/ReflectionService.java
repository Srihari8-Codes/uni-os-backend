package com.unios.service.agents.framework.v5;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unios.model.AgentStrategy;
import com.unios.repository.AgentStrategyRepository;
import com.unios.service.llm.ResilientLLMService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReflectionService {

    private final ResilientLLMService llmService;
    private final AgentStrategyRepository strategyRepository;
    private final ObjectMapper objectMapper;

    private static final String REFLECTION_PROMPT_TEMPLATE = """
            You are the Metacognitive Reflection Engine for University OS v5.
            Your task is to analyze the execution trace of the following agent goal and extract structured strategic updates.

            GOAL CONTEXT: %s
            SUCCESS METRICS: %s
            
            FULL EXECUTION HISTORY:
            %s

            OUTPUT SCHEMA REQUIREMENT:
            You MUST return ONLY a valid JSON object matching this exact structure:
            {
              "insights": [ "List of core reasons why it succeeded or failed" ],
              "improvements": [ "Specific corrections for tool usage or parameters" ],
              "strategy_updates": [ "A generalized, high-level rule for future executions. Avoid vague advice. Be precise." ]
            }

            RULES:
            1. Reject low-quality insights: Strategies like 'Be more careful' or 'Try again' are invalid. Provide concrete conditions and actions.
            2. Ignore contradictory data, synthesize the final concrete outcome.
            3. Return raw JSON only. Do not format with markdown blocks like ```json.
            """;

    @Transactional
    public void runPostMortemReflection(String goalContext, String successMetrics, String executionHistory) {
        log.info("Initiating Reflection Post-Mortem for Goal: {}", goalContext);
        
        String prompt = String.format(REFLECTION_PROMPT_TEMPLATE, goalContext, successMetrics, executionHistory);

        try {
            String rawResponse = llmService.executeWithResilience(prompt, "REFLECT").getFullResponse();
            processAndStoreReflection(goalContext, rawResponse, successMetrics);
        } catch (Exception e) {
            log.error("Failed to run reflection post-mortem: {}", e.getMessage());
        }
    }

    private void processAndStoreReflection(String goalContext, String rawResponse, String successMetrics) throws JsonProcessingException {
        // Strip markdown blocks if present
        String cleanedJson = rawResponse.replaceAll("^```json\\s*", "").replaceAll("```$", "").trim();
        JsonNode rootNode = objectMapper.readTree(cleanedJson);

        if (!rootNode.has("strategy_updates") || !rootNode.get("strategy_updates").isArray()) {
            throw new IllegalArgumentException("Reflection JSON missing 'strategy_updates' array");
        }

        // Determine base scoring from success metrics (Simple heuristic: 0.8 if SUCCESS, 0.4 if FAILURE)
        double baseScore = successMetrics.toUpperCase().contains("SUCCESS") ? 0.8 : 0.4;

        Iterator<JsonNode> elements = rootNode.get("strategy_updates").elements();
        while (elements.hasNext()) {
            String strategyText = elements.next().asText().trim();
            
            // Filter vague/low-quality insights (Edge case check)
            if (strategyText.length() < 20 || strategyText.toLowerCase().contains("be careful")) {
                log.warn("Discarded low-quality strategy update: {}", strategyText);
                continue;
            }

            storeOrUpdateStrategy(goalContext, strategyText, baseScore);
        }
    }

    private void storeOrUpdateStrategy(String goalContext, String strategyText, double updateScore) {
        Optional<AgentStrategy> existingOpt = strategyRepository.findByGoalContextAndStrategy(goalContext, strategyText);

        if (existingOpt.isPresent()) {
            AgentStrategy existing = existingOpt.get();
            // Conflicting strategy resolution: Decay or reinforce score via Moving Average
            double newScore = (existing.getConfidenceScore() * 0.7) + (updateScore * 0.3);
            existing.setConfidenceScore(Math.min(1.0, Math.max(0.1, newScore))); // Bound strictly between 0.1 and 1.0
            existing.setUsageCount(existing.getUsageCount() + 1);
            strategyRepository.save(existing);
            log.info("Updated existing strategy. New Score: {}", newScore);
        } else {
            AgentStrategy newStrategy = AgentStrategy.builder()
                    .goalContext(goalContext)
                    .strategy(strategyText)
                    .confidenceScore(updateScore)
                    .usageCount(1)
                    .build();
            strategyRepository.save(newStrategy);
            log.info("Learned new strategy for context [{}]: {}", goalContext, strategyText);
        }
    }

    /**
     * Interface for the ReasoningEngine to inject learned strategies into the next planning cycle.
     */
    @Transactional(readOnly = true)
    public String getActiveStrategies(String goalContext) {
        List<AgentStrategy> topStrategies = strategyRepository.findTopRelevantStrategies(goalContext);
        
        if (topStrategies.isEmpty()) {
            return "No historical strategies learned for this context yet.";
        }

        return topStrategies.stream()
                .map(s -> String.format("- [Score: %.2f] %s", s.getConfidenceScore(), s.getStrategy()))
                .collect(Collectors.joining("\n"));
    }
}
