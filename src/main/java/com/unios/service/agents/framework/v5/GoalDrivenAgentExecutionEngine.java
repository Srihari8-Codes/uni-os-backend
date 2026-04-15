package com.unios.service.agents.framework.v5;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Core loop for the v5 Goal-Driven Agentic System.
 * Ensures robust execution with cycle limits, retry mechanisms, and memory trace logging.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GoalDrivenAgentExecutionEngine {

    private final ReasoningEngine reasoningEngine;
    private final ToolExecutor toolExecutor;
    private final Evaluator evaluator;
    private final MemoryService memoryService;

    private static final int MAX_ITERATIONS = 10;
    private static final int MAX_CONSECUTIVE_FAILURES = 3;

    public boolean executeGoal(String agentId, String goalId, String goalDescription, Map<String, Object> context) {
        log.info("[{}] Initiating Agent Execution for Goal: {}", agentId, goalId);

        int iteration = 0;
        int consecutiveFailures = 0;
        boolean goalAchieved = false;

        StringBuilder executionTrace = new StringBuilder();
        executionTrace.append("Execution started for goal: ").append(goalDescription).append("\n");

        AgentPlan plan = null;

        while (iteration < MAX_ITERATIONS && !goalAchieved && consecutiveFailures < MAX_CONSECUTIVE_FAILURES) {
            iteration++;
            log.info("[{}] Cycle {} - Generating/Validating Plan", agentId, iteration);

            try {
                // 1. Memory Context Retrieval
                String memoryContext = memoryService.retrieveContext(agentId, goalId);

                // 2. Planning
                if (plan == null) {
                    plan = reasoningEngine.generatePlan(goalDescription, context, memoryContext);
                }

                if (plan == null || (plan.getToolName() == null && !plan.isComplete())) {
                    log.error("[{}] PLAN ERROR: Null or invalid plan generated.", agentId);
                    consecutiveFailures++;
                    executionTrace.append("[CYCLE-").append(iteration).append("] FAILED: Null/Invalid Plan\n");
                    plan = reasoningEngine.replan(goalDescription, context, memoryContext, "Last plan was null or invalid.");
                    continue; // Retry with replan
                }

                if (plan.isComplete()) {
                    log.info("[{}] Agent determined goal is already complete during planning.", agentId);
                    goalAchieved = true;
                    executionTrace.append("[CYCLE-").append(iteration).append("] AGENT DECLARED COMPLETE\n");
                    break;
                }

                // 3. Execution
                log.info("[{}] Executing Tool: {} with params: {}", agentId, plan.getToolName(), plan.getParameters());
                AgentStepResult result = toolExecutor.executeWaitable(plan.getToolName(), plan.getParameters());
                
                executionTrace.append("[CYCLE-").append(iteration).append("] TOOL: ").append(plan.getToolName())
                              .append(" | SUCCESS: ").append(result.isSuccess()).append("\n");

                // Check for Tool Crash
                if (!result.isSuccess() && result.getErrorMessage() != null) {
                    log.warn("[{}] Tool execution failed: {}", agentId, result.getErrorMessage());
                    consecutiveFailures++;
                    plan = reasoningEngine.replan(goalDescription, context, memoryContext, "Tool crash: " + result.getErrorMessage());
                    continue;
                }

                // 4. Evaluation
                log.info("[{}] Evaluating result...", agentId);
                EvaluationResult eval = evaluator.evaluateResult(goalDescription, plan, result);

                executionTrace.append("[CYCLE-").append(iteration).append("] EVAL: GoalAchieved=").append(eval.isGoalAchieved())
                              .append(" | NeedsReplan=").append(eval.isNeedsReplan()).append("\n");

                // Reset failure counter on a valid, successful cycle
                consecutiveFailures = 0;

                // 5. Memory Storage (Short-term context update)
                memoryService.storeShortTerm(agentId, "lastAction", plan.getToolName());
                memoryService.storeShortTerm(agentId, "lastResult", result.getOutput());

                if (eval.isGoalAchieved()) {
                    log.info("[{}] Goal achieved successfully on cycle {}.", agentId, iteration);
                    goalAchieved = true;
                } else if (eval.isNeedsReplan()) {
                    log.info("[{}] Evaluator triggered replan. Reason: {}", agentId, eval.getAnalysis());
                    plan = reasoningEngine.replan(goalDescription, context, memoryContext, eval.getAnalysis());
                } else {
                    // Loop continues with the current plan (e.g. multi-step plan sequence if applicable, or requests next step)
                    plan = null; // Clear to force next sequence step generation in next iteration
                }

            } catch (Exception e) {
                log.error("[{}] CRITICAL FAULT during cycle {}: {}", agentId, iteration, e.getMessage(), e);
                consecutiveFailures++;
                executionTrace.append("[CYCLE-").append(iteration).append("] EXCEPTION: ").append(e.getMessage()).append("\n");
                plan = null; // Force replan on exception
            }
        }

        // Post-Loop Analysis
        if (!goalAchieved) {
            if (iteration >= MAX_ITERATIONS) {
                log.error("[{}] ABORTED: Reached max iterations ({}) without achieving goal.", agentId, MAX_ITERATIONS);
                executionTrace.append("ABORTED: INFINITE LOOP PROTECTION TRIGGERED\n");
            } else if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                log.error("[{}] ABORTED: Reached max consecutive failures ({}).", agentId, MAX_CONSECUTIVE_FAILURES);
                executionTrace.append("ABORTED: CASCADING FAILURE PROTECTION TRIGGERED\n");
            }
        }

        // 6. Trace Storage (Long term memory)
        memoryService.storeTrace(goalId, executionTrace.toString());
        log.info("[{}] Execution Trace Stored. Final Status: Achieved = {}", agentId, goalAchieved);

        return goalAchieved;
    }
}
