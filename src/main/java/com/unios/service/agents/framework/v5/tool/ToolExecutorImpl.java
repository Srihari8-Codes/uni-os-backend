package com.unios.service.agents.framework.v5.tool;

import com.unios.service.agents.framework.v5.AgentStepResult;
import com.unios.service.agents.framework.v5.ToolExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class ToolExecutorImpl implements ToolExecutor {

    private final ToolRegistry toolRegistry;

    @Override
    public AgentStepResult executeWaitable(String toolName, Map<String, Object> parameters) {
        Tool tool = toolRegistry.getTool(toolName);
        
        if (tool == null) {
            log.error("Tool execution failed: Unknown tool '{}'", toolName);
            return AgentStepResult.builder()
                    .success(false)
                    .errorMessage("Unknown tool: " + toolName)
                    .build();
        }

        log.info("Executing tool '{}' with parameters {}", toolName, parameters);
        
        try {
            String output = tool.execute(parameters);
            log.info("Tool '{}' executed successfully. Output: {}", toolName, output);
            return AgentStepResult.builder()
                    .success(true)
                    .output(output)
                    .build();
        } catch (Exception e) {
            log.error("Tool '{}' execution failed: {}. Attempting rollback.", toolName, e.getMessage());
            
            try {
                tool.rollback(parameters);
                log.info("Rollback successful for tool '{}'", toolName);
            } catch (Exception rollbackEx) {
                log.error("CRITICAL: Rollback failed for tool '{}': {}", toolName, rollbackEx.getMessage());
            }

            return AgentStepResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }
}
