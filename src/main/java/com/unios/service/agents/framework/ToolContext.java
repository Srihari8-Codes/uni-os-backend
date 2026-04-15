package com.unios.service.agents.framework;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * ToolContext — the rich input package passed to every intelligent tool.
 *
 * Replaces the raw Map<String, Object> pattern and forces tools
 * to reason contextually rather than acting blindly on parameters alone.
 */
@Data
@Builder
public class ToolContext {

    /**
     * Domain-specific parameters provided by the LLM planner
     * (e.g., batchId, studentId, reason, threshold).
     */
    private Map<String, Object> parameters;

    /**
     * History string from AgentMemoryService (previous decisions for this agent/entity).
     * Used by tools to avoid repeating failed strategies.
     */
    @Builder.Default
    private String agentHistory = "";

    /**
     * Current university system state snapshot.
     * e.g., {"vacancyRate": 0.23, "atRiskStudentCount": 14}
     */
    @Builder.Default
    private Map<String, Object> systemState = Map.of();

    /**
     * Hard constraints the tool must respect.
     * e.g., {"maxPromotions": 5, "minScore": 70.0}
     */
    @Builder.Default
    private Map<String, Object> constraints = Map.of();
}
