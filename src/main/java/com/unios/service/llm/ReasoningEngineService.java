package com.unios.service.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unios.dto.DecisionResponse;
import com.unios.model.AgentDecisionLog;
import com.unios.repository.AgentDecisionLogRepository;
import org.springframework.stereotype.Service;

@Service
public class ReasoningEngineService {

    private final ResilientLLMService resilientLLMService;
    private final AgentDecisionLogRepository decisionLogRepository;

    public ReasoningEngineService(ResilientLLMService resilientLLMService, 
                                  AgentDecisionLogRepository decisionLogRepository) {
        this.resilientLLMService = resilientLLMService;
        this.decisionLogRepository = decisionLogRepository;
    }

    /**
     * Legacy support for simple string responses.
     */
    public String decide(String agentName, String entityType, Long entityId, String context, String inputData) {
        DecisionResponse response = decideStructured(agentName, entityType, entityId, context, inputData);
        return String.format("%s (%s)", response.getAction(), response.getReasoning());
    }

    /**
     * Centralized structured decision-making hook for agents.
     */
    public DecisionResponse decideStructured(String agentName, String entityType, Long entityId, String context, String inputData) {
        String systemPrompt = String.format(
            "You are the '%s', a sovereign AI agent in University OS. your mission is to achieve the user's goal with 100%% autonomy.\n\n" +
            "AVAILABLE SUPER-TOOLS:\n" +
            "- DOCUMENT_ANALYZER: Extract and verify grades from PDFs. Use this before promotion to ensure merit.\n" +
            "- SEND_VOICE_NOTE: Generate a personalized voice note and send via SMS/Email. Use for interventions.\n" +
            "- AI_VOICE_CALL: [SHADOW] Pre-plan a voice call to a parent. Use for critical escalations.\n" +
            "- PROMOTE_WAITLIST: Move waitlisted students to admitted status.\n" +
            "- ADMISSIONS_AUDIT: Find vacancy hotspots in batches.\n\n" +
            "RULES:\n" +
            "1. Output ONLY a valid JSON object.\n" +
            "2. 'action': Name of the tool to call, or 'FINALIZE' if the goal is fully achieved.\n" +
            "3. 'reasoning': Explicit 'Chain-of-Thought'. Explain WHY you chose this action and what you expect to achieve.\n" +
            "4. 'parameters': Map of parameters for the tool.\n" +
            "5. 'confidence': Float score of your certainty.\n\n" +
            "CONTEXT: %s",
            agentName, context
        );

        DecisionResponse decisionResponse = resilientLLMService.executeWithResilience(systemPrompt, inputData);

        // Log the decision for transparency
        AgentDecisionLog log = new AgentDecisionLog(
            agentName, 
            entityType, 
            entityId, 
            decisionResponse.getAction(), 
            decisionResponse.getReasoning()
        );
        decisionLogRepository.save(log);

        return decisionResponse;
    }
}
