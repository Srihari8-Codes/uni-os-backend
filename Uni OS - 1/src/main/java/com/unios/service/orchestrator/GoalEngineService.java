package com.unios.service.orchestrator;

import com.unios.model.Goal;
import com.unios.repository.GoalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class GoalEngineService {

    private final GoalRepository goalRepository;

    @Transactional
    public Goal createGoal(Goal.GoalType type, String description, Map<String, Object> constraints, 
                           Map<String, Object> successCriteria, ZonedDateTime deadline) {
        
        validateConfig(constraints, successCriteria);

        Goal goal = Goal.builder()
                .type(type)
                .name(description)          // required, not-null column
                .description(description)
                .category(type.name())      // populate category from type for consistency
                .constraints(constraints)
                .successCriteria(successCriteria)
                .status(Goal.GoalStatus.ACTIVE)
                .progressMetrics(Map.of())
                .deadline(deadline)
                .build();

        return goalRepository.save(goal);
    }

    @Transactional
    public Goal updateProgress(Long goalId, Map<String, Object> newMetrics) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new IllegalArgumentException("Goal not found: " + goalId));

        if (goal.getStatus() != Goal.GoalStatus.ACTIVE) {
            log.warn("Attempting to update progress for non-active goal: {}", goalId);
            return goal;
        }

        goal.getProgressMetrics().putAll(newMetrics);
        goalRepository.save(goal);
        
        return evaluateCompletion(goalId);
    }

    @Transactional
    public Goal evaluateCompletion(Long goalId) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new IllegalArgumentException("Goal not found: " + goalId));

        if (goal.getStatus() != Goal.GoalStatus.ACTIVE) return goal;
        if (goal.getDeadline() != null && ZonedDateTime.now().isAfter(goal.getDeadline())) {
            return handleTimeout(goal);
        }

        boolean conflicts = checkConflicts(goal.getConstraints(), goal.getProgressMetrics());
        if (conflicts) {
            goal.setStatus(Goal.GoalStatus.FAILED);
            return goalRepository.save(goal);
        }

        double completionThreshold = calculateCompletionPercentage(goal.getSuccessCriteria(), goal.getProgressMetrics());
        
        if (completionThreshold >= 100.0) {
            goal.setStatus(Goal.GoalStatus.COMPLETED);
        } else if (completionThreshold > 0.0 && isNearingDeadline(goal)) {
            goal.setStatus(Goal.GoalStatus.PARTIALLY_COMPLETED);
        }

        return goalRepository.save(goal);
    }

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void sweepTimeouts() {
        int updatedCount = goalRepository.markExpiredGoalsAsTimeout(ZonedDateTime.now());
        if (updatedCount > 0) {
            log.info("GoalEngine: Marked {} active goals as TIMEOUT.", updatedCount);
        }
    }

    private boolean checkConflicts(Map<String, Object> constraints, Map<String, Object> metrics) {
        if (constraints != null && metrics != null && constraints.containsKey("maxExpenditure") && metrics.containsKey("currentExpenditure")) {
            double max = getAsDouble(constraints.get("maxExpenditure"));
            double current = getAsDouble(metrics.get("currentExpenditure"));
            return current > max;
        }
        return false;
    }

    private double calculateCompletionPercentage(Map<String, Object> criteria, Map<String, Object> metrics) {
        if (criteria == null || criteria.isEmpty()) return 0.0;
        
        double totalWeight = 0;
        double achievedWeight = 0;

        for (Map.Entry<String, Object> entry : criteria.entrySet()) {
            String targetKey = entry.getKey();
            double targetValue = getAsDouble(entry.getValue());
            double actualValue = metrics != null ? getAsDouble(metrics.getOrDefault(targetKey, 0.0)) : 0.0;
            
            totalWeight += 1.0; 
            achievedWeight += Math.min(1.0, actualValue / targetValue);
        }
        
        return (achievedWeight / totalWeight) * 100.0;
    }

    private Goal handleTimeout(Goal goal) {
        double currentCompletion = calculateCompletionPercentage(goal.getSuccessCriteria(), goal.getProgressMetrics());
        goal.setStatus(currentCompletion > 0 ? Goal.GoalStatus.PARTIALLY_COMPLETED : Goal.GoalStatus.TIMEOUT);
        return goalRepository.save(goal);
    }

    private boolean isNearingDeadline(Goal goal) {
        if (goal.getDeadline() == null) return false;
        return ZonedDateTime.now().plusHours(1).isAfter(goal.getDeadline());
    }

    private void validateConfig(Map<String, Object> constraints, Map<String, Object> successCriteria) {
        if (successCriteria == null || successCriteria.isEmpty()) {
            throw new IllegalArgumentException("Success criteria must be defined.");
        }
    }

    private double getAsDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
