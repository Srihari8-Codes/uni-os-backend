package com.unios.service.agents.framework;

import com.unios.model.AgentActionOutcome;
import com.unios.repository.AgentActionOutcomeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AgentLearningService — core of the autonomous learning loop.
 *
 * This service analyzes historical ToolResults to determine which tools
 * are most effective for different types of goals and system states.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AgentLearningService {

    private final AgentActionOutcomeRepository outcomeRepository;

    /**
     * Records the outcome of an agent's action.
     * Maps ToolResult status to a numerical effectiveness score.
     */
    public void recordOutcome(String taskId, String agentName, String toolName, 
                              String reasoning, ToolResult result, String healthContext) {
        
        double score = mapStatusToScore(result.getStatus());
        
        AgentActionOutcome outcome = AgentActionOutcome.builder()
                .taskId(taskId)
                .agentName(agentName)
                .toolName(toolName)
                .reasoning(reasoning)
                .status(result.getStatus())
                .effectivenessScore(score)
                .contextHealth(healthContext)
                .timestamp(LocalDateTime.now())
                .build();

        outcomeRepository.save(outcome);
        log.info("[LEARNING LOOP] Outcome recorded: Tool={} | Status={} | Score={}", 
                toolName, result.getStatus(), score);
    }

    /**
     * Generates a "Knowledge Summary" for the LLM planning prompt.
     * 
     * Prioritizes tools with high historical success and warns about
     * tools that have failed recently.
     */
    public String getLearningSummary() {
        List<Object[]> stats = outcomeRepository.getAverageEffectivenessPerTool();
        if (stats.isEmpty()) {
            return "No historical performance data available yet. Operate based on primary instructions.";
        }

        StringBuilder sb = new StringBuilder("HISTORICAL TOOL PERFORMANCE:\n");

        for (Object[] row : stats) {
            String tool = (String) row[0];
            Double avgEffectiveness = (Double) row[1];
            
            // Check for recent failures (last 1 hour)
            long recentFailures = outcomeRepository.countRecentFailures(tool, LocalDateTime.now().minusHours(1));
            
            String performanceTip = avgEffectiveness >= 0.8 ? "EXCELLENT" : 
                                     avgEffectiveness >= 0.5 ? "RELIABLE" : "UNRELIABLE";

            sb.append(String.format("- %s: %s (Avg Success: %.0f%%)", tool, performanceTip, avgEffectiveness * 100));
            if (recentFailures > 0) {
                sb.append(String.format(" ⚠ FAILED %d times in the last hour. Use with CAUTION.", recentFailures));
            }
            sb.append("\n");
        }

        sb.append("\nADAPTATION STRATEGY: Prioritize tools marked 'EXCELLENT' for the current state. " +
                  "If a tool has recent failures, try an alternative or reflect on the input parameters.");

        return sb.toString();
    }

    private double mapStatusToScore(String status) {
        if (status == null) return 0.5;
        return switch (status.toUpperCase()) {
            case "SUCCESS" -> 1.0;
            case "PARTIAL" -> 0.5;
            case "NO_ACTION_NEEDED" -> 0.8;
            case "FAILED" -> 0.0;
            default -> 0.5;
        };
    }
}
