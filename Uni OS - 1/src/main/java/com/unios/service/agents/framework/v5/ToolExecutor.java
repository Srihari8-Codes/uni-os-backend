package com.unios.service.agents.framework.v5;

public interface ToolExecutor {
    AgentStepResult executeWaitable(String toolName, java.util.Map<String, Object> parameters);
}
