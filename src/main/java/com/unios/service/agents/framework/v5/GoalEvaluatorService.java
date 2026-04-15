package com.unios.service.agents.framework.v5;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * GoalEvaluatorService — concrete implementation of the Evaluator interface.
 *
 * Evaluates the result of each tool execution and determines:
 * 1. Whether the overall goal has been achieved (e.g., "COMPLETE" tool used).
 * 2. Whether the agent needs to replan (e.g., tool returned a failure).
 * 3. Analysis text to feed back into the next planning prompt.
 */
@Service
@Slf4j
public class GoalEvaluatorService implements Evaluator {

    @Override
    public EvaluationResult evaluateResult(String goal, AgentPlan plan, AgentStepResult result) {
        log.debug("[EVALUATOR] Evaluating result for goal: {}", goal);

        // Case 1: The planner explicitly declared the goal complete
        if (plan.isComplete()) {
            log.info("[EVALUATOR] Goal declared COMPLETE by planner.");
            return EvaluationResult.builder()
                    .goalAchieved(true)
                    .needsReplan(false)
                    .analysis("Planner triggered COMPLETE signal.")
                    .build();
        }

        // Case 2: Tool execution itself failed
        if (!result.isSuccess()) {
            String errorMsg = result.getErrorMessage() != null ? result.getErrorMessage() : "Unknown tool error";
            log.warn("[EVALUATOR] Tool '{}' failed: {}", plan.getToolName(), errorMsg);
            return EvaluationResult.builder()
                    .goalAchieved(false)
                    .needsReplan(true)
                    .analysis("Tool '" + plan.getToolName() + "' failed with: " + errorMsg + ". Replan required.")
                    .build();
        }

        // Case 3: Tool succeeded — check if the output signals completion
        String output = result.getOutput() != null ? result.getOutput().toLowerCase() : "";
        if (output.contains("complete") || output.contains("all seats filled") || output.contains("goal achieved")) {
            log.info("[EVALUATOR] Tool output signals goal completion.");
            return EvaluationResult.builder()
                    .goalAchieved(true)
                    .needsReplan(false)
                    .analysis("Tool output indicates goal is fully achieved: " + result.getOutput())
                    .build();
        }

        // Case 4: Tool ran successfully but goal continues — advance to next step
        log.info("[EVALUATOR] Tool '{}' succeeded. Continuing execution cycle.", plan.getToolName());
        return EvaluationResult.builder()
                .goalAchieved(false)
                .needsReplan(false)
                .analysis("Step '" + plan.getToolName() + "' completed. Output: " + result.getOutput())
                .build();
    }
}
