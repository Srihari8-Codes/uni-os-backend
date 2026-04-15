package com.unios.service.agents.framework.v5;

public interface Evaluator {
    EvaluationResult evaluateResult(String goal, AgentPlan plan, AgentStepResult result);
}
