package com.unios.service.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unios.dto.DecisionResponse;
import com.unios.service.test.FailureInjectionService;
import com.unios.service.orchestrator.InstitutionalOrchestrator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.*;
import java.util.function.Supplier;

@Service
@Slf4j
public class ResilientLLMService {

    private final LLMClient primaryClient; // Usually Ollama
    private final LLMClient fallbackClient; // Local Rule-Based
    private final ObjectMapper objectMapper;
    private final ExecutorService executor = Executors.newFixedThreadPool(10);

    private boolean circuitOpen = false;
    private int failureCount = 0;
    private static final int CIRCUIT_THRESHOLD = 5;

    private final FailureInjectionService failureInjectionService;

    public ResilientLLMService(@Qualifier("ollamaClient") LLMClient primaryClient,
                               @Qualifier("localRuleBasedClient") LLMClient fallbackClient,
                               ObjectMapper objectMapper,
                               FailureInjectionService failureInjectionService) {
        this.primaryClient = primaryClient;
        this.fallbackClient = fallbackClient;
        this.objectMapper = objectMapper;
        this.failureInjectionService = failureInjectionService;
    }

    public DecisionResponse executeWithResilience(String systemPrompt, String userPrompt) {
        failureInjectionService.checkLlm();
        logToOrch("[RESILIENCE] Processing LLM Request for: " + (userPrompt.length() > 50 ? userPrompt.substring(0, 50) + "..." : userPrompt));
        
        if (circuitOpen) {
            log.warn("[RESILIENCE] Circuit is OPEN. Forcing fallback.");
            return executeFallback(systemPrompt, userPrompt);
        }

        try {
            return retryOnFailure(() -> callProvider(systemPrompt, userPrompt), 2);
        } catch (Exception e) {
            log.error("[RESILIENCE] Primary LLM failed after retries. Entering fallback. Error: {}", e.getMessage());
            incrementFailures();
            return executeFallback(systemPrompt, userPrompt);
        }
    }

    private DecisionResponse callProvider(String systemPrompt, String userPrompt) {
        CompletableFuture<String> future = CompletableFuture.supplyAsync(
            () -> primaryClient.generateResponse(systemPrompt, userPrompt), executor);

        try {
            String response = future.get(60, TimeUnit.SECONDS); // 60s Timeout for OCR/LLM
            logToOrch("[RESILIENCE] RAW Response: " + (response.length() > 100 ? response.substring(0, 100) + "..." : response));
            DecisionResponse dr = objectMapper.readValue(response, DecisionResponse.class);
            
            // Populate traces for governance
            dr.setFullPrompt(systemPrompt + "\n---\n" + userPrompt);
            dr.setFullResponse(response);
            
            resetFailures(); // Success!
            return dr;
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new RuntimeException("LLM Timeout");
        } catch (Exception e) {
            throw new RuntimeException("LLM Execution/Parsing Error: " + e.getMessage());
        }
    }

    private DecisionResponse retryOnFailure(Supplier<DecisionResponse> action, int maxRetries) {
        int attempts = 0;
        while (attempts < maxRetries) {
            try {
                return action.get();
            } catch (Exception e) {
                attempts++;
                log.warn("[RESILIENCE] Attempt {} failed. Retrying...", attempts);
                if (attempts >= maxRetries) throw e;
            }
        }
        return null;
    }

    private DecisionResponse executeFallback(String systemPrompt, String userPrompt) {
        logToOrch("[RESILIENCE] Entering Fallback Mode (Local Rules)...");
        String response = fallbackClient.generateResponse(systemPrompt, userPrompt);
        logToOrch("[RESILIENCE] Fallback RAW: " + response);
        try {
            return objectMapper.readValue(response, DecisionResponse.class);
        } catch (Exception e) {
            log.error("[RESILIENCE] Critical Error: Fallback failed to produce valid JSON!");
            return new DecisionResponse("ERROR", "Total system failure", new java.util.HashMap<>(), 0.0);
        }
    }

    private void logToOrch(String msg) {
        InstitutionalOrchestrator.ACTIVITY_LOG.add("[LLM] " + java.time.LocalDateTime.now() + ": " + msg);
    }

    private synchronized void incrementFailures() {
        failureCount++;
        if (failureCount >= CIRCUIT_THRESHOLD) {
            circuitOpen = true;
            log.error("[RESILIENCE] Circuit Breaker TRIPPED! Primary LLM is now offline.");
        }
    }

    private synchronized void resetFailures() {
        failureCount = 0;
        circuitOpen = false;
    }
}
