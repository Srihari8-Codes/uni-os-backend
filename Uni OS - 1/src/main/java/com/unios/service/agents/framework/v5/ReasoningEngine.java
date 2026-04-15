package com.unios.service.agents.framework.v5;

import java.util.Map;

public interface ReasoningEngine {
    AgentPlan generatePlan(String goal, Map<String, Object> context, String memoryContext);
    AgentPlan replan(String goal, Map<String, Object> context, String memoryContext, String failureReason);
}
