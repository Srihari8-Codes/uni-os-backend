package com.unios.service.agents.framework.v5;

import java.util.List;
import com.unios.model.AgentExperience;

public interface MemoryService {
    void storeShortTerm(String agentId, String key, Object value);
    void storeTrace(String goalId, String executionTrace);
    String retrieveContext(String agentId, String goalId);
    
    // v5 Requirements
    void storeExperience(String goalId, String action, String result, String outcome, boolean isSuccess);
    List<AgentExperience> fetchRelevantMemory(String goalId, String actionContext);
    void pruneMemory();
}
