package com.unios.service.agents.framework;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * ToolResult — the reasoning-rich output of every intelligent tool.
 *
 * Goes beyond a simple "action taken" message. Every tool must explain
 * WHY it made the choice it did, with what confidence, and what alternatives
 * were considered but rejected. This feeds back into the LLM's reflection phase.
 */
@Data
@Builder
public class ToolResult {

    /** One-line summary of what the tool did (replaces raw String output) */
    private String summary;

    /** Detailed step-by-step reasoning chain explaining the decision */
    private String reasoning;

    /**
     * Confidence in the decision, 0.0–1.0.
     * Low confidence flags the result for LLM reflection + possible pivot.
     */
    @Builder.Default
    private double confidence = 1.0;

    /** Structured data about the primary action taken */
    private Map<String, Object> actionData;

    /**
     * Ranked alternatives considered but not chosen.
     * Each entry: {"candidate": "...", "score": 0.82, "rejectedReason": "..."}
     * Gives the LLM visibility into the decision space.
     */
    private List<Map<String, Object>> rankedAlternatives;

    /** Whether this result should trigger the LLM to take a follow-up action */
    @Builder.Default
    private boolean requiresFollowUp = false;

    /** Suggested next tool/action for the LLM's planning phase */
    private String suggestedNextAction;

    /** Outcome status: SUCCESS, PARTIAL, FAILED, NO_ACTION_NEEDED */
    @Builder.Default
    private String status = "SUCCESS";
}
