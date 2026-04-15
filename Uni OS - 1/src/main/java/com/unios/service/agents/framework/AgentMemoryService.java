package com.unios.service.agents.framework;

import com.unios.model.AgentDecisionLog;
import com.unios.repository.AgentDecisionLogRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class AgentMemoryService {

    private final AgentDecisionLogRepository decisionLogRepository;
    
    // Short-term memory (in-memory cache for current workflow)
    private final Map<String, Map<String, Object>> shortTermMemory = new ConcurrentHashMap<>();

    public AgentMemoryService(AgentDecisionLogRepository decisionLogRepository) {
        this.decisionLogRepository = decisionLogRepository;
    }

    public void setShortTerm(String agentName, String key, Object value) {
        shortTermMemory.computeIfAbsent(agentName, k -> new ConcurrentHashMap<>()).put(key, value);
    }

    public Object getShortTerm(String agentName, String key) {
        return shortTermMemory.getOrDefault(agentName, Map.of()).get(key);
    }

    public String getLongTermContext(String agentName, String entityType, Long entityId) {
        List<AgentDecisionLog> history = decisionLogRepository.findByEntityTypeAndEntityId(entityType, entityId);
        
        if (history.isEmpty()) {
            return "No prior history for this entity.";
        }

        return history.stream()
                .map(log -> String.format("[%s] Action: %s | Reason: %s", 
                        log.getAgentName(), log.getDecision(), log.getReasoning()))
                .collect(Collectors.joining("\n"));
    }
    
    public void clearShortTerm(String agentName) {
        shortTermMemory.remove(agentName);
    }
}
