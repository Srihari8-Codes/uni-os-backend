package com.unios.service.agents.framework;

import java.util.Map;

/**
 * AgentTool — upgraded to support contextual decision-making.
 *
 * All tools must now:
 *   1. Accept a rich context map (state, history, constraints)
 *   2. Return a ToolResult that includes not just the action output,
 *      but reasoning metadata, confidence, and ranked alternatives.
 *
 * The legacy execute(Map) method is kept for backward compatibility
 * and delegates to executeWithContext by default.
 */
public interface AgentTool {

    /** Tool identifier used by the LLM to select this tool */
    String name();

    /** Human-readable description given to the LLM in the system prompt */
    String description();

    /**
     * Primary intelligent execution method.
     * Input context may include:
     *   - "history"     : past decisions for this agent
     *   - "constraints" : hard limits (capacity, deadlines)
     *   - "state"       : current university KPIs
     *   - any domain-specific parameters the LLM provides
     *
     * Returns a ToolResult with action, reasoning, confidence, and alternatives.
     */
    ToolResult executeWithContext(ToolContext context);

    /**
     * Legacy compatibility shim. Default implementation wraps
     * raw map in a ToolContext and returns result as string.
     */
    default Object execute(Map<String, Object> input) {
        ToolContext ctx = ToolContext.builder()
                .parameters(input)
                .build();
        ToolResult result = executeWithContext(ctx);
        return result.getSummary() + " [confidence=" + result.getConfidence() + "]";
    }
}
