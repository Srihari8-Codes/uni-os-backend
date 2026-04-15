package com.unios.service.agents.framework.v5;

import com.unios.model.AgentExperience;
import com.unios.repository.AgentExperienceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class MemoryServiceImpl implements MemoryService {

    private final AgentExperienceRepository experienceRepository;

    // Short-term in-memory storage for current cycle context
    private final Map<String, Map<String, Object>> shortTermMemory = new ConcurrentHashMap<>();
    
    private static final int MAX_DB_RECORDS = 50000;
    private static final double LOW_SCORE_THRESHOLD = 0.3;

    @Override
    public void storeShortTerm(String agentId, String key, Object value) {
        shortTermMemory.computeIfAbsent(agentId, k -> new ConcurrentHashMap<>()).put(key, value);
    }

    @Override
    public void storeTrace(String goalId, String executionTrace) {
        log.info("Execution trace for goal {} stored temporarily (not persisted to DB in this model).", goalId);
        // Can be routed to a NoSQL or Blob store in production
    }

    @Override
    @Transactional(readOnly = true)
    public String retrieveContext(String agentId, String goalId) {
        Map<String, Object> threadMemory = shortTermMemory.getOrDefault(agentId, Map.of());
        
        // Fetch top recent memory relevant to this goal
        List<AgentExperience> longTerm = experienceRepository.findByGoalIdOrderByScoreDesc(goalId)
                .stream()
                .limit(5)
                .collect(Collectors.toList());

        StringBuilder ctx = new StringBuilder("SHORT_TERM:\n")
                .append(threadMemory.toString()).append("\n\n")
                .append("LONG_TERM_LESSONS:\n");

        if (longTerm.isEmpty()) {
            ctx.append("No historical data available for this goal.");
        } else {
            for (AgentExperience exp : longTerm) {
                ctx.append(String.format("- Action [%s] resulting in [%s] achieved score %.2f\n",
                        exp.getAction(), exp.getOutcome(), exp.getScore()));
            }
        }
        return ctx.toString();
    }

    @Override
    @Transactional
    public void storeExperience(String goalId, String action, String result, String outcome, boolean isSuccess) {
        // Scoring Mechanism
        double scoreUpdate = isSuccess ? 1.0 : 0.0;

        // Duplicate/Conflicting Outcomes check
        Optional<AgentExperience> existingOpt = experienceRepository.findFirstByGoalIdAndActionAndOutcome(goalId, action, outcome);

        if (existingOpt.isPresent()) {
            AgentExperience existing = existingOpt.get();
            // EMA (Exponential Moving Average) to handle conflicting outcomes
            double newScore = (existing.getScore() * 0.7) + (scoreUpdate * 0.3); 
            existing.setScore(newScore);
            existing.setResult(result); // Update with latest context/result string
            experienceRepository.save(existing);
            log.info("Updated existing experience [Goal: {}, Action: {}, New Score: {:.2f}]", goalId, action, newScore);
        } else {
            AgentExperience newExperience = AgentExperience.builder()
                    .goalId(goalId)
                    .action(action)
                    .result(result)
                    .outcome(outcome)
                    .score(scoreUpdate)
                    .build();
            experienceRepository.save(newExperience);
            log.info("Stored new experience [Goal: {}, Action: {}, Score: {}]", goalId, action, scoreUpdate);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgentExperience> fetchRelevantMemory(String goalId, String actionContext) {
        // Filtering logic: Fetch top 5 relevant experiences for a specific action across all goals with a score > 0.5
        return experienceRepository.findTopRelevantByAction(actionContext);
    }

    @Override
    @Scheduled(cron = "0 0 2 * * ?") // Daily at 2 AM
    @Transactional
    public void pruneMemory() {
        log.info("Initating Memory Pruning sequence...");

        // 1. Prune noisy data: Remove old entries with very low scores (consistently bad or irrelevant)
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        int clearedNoisy = experienceRepository.pruneLowScoringMemories(LOW_SCORE_THRESHOLD, cutoff);
        log.info("Pruned {} low-scoring old memories.", clearedNoisy);

        // 2. Prevent Memory Overflow (Hard cap on DB size)
        long totalRecords = experienceRepository.count();
        if (totalRecords > MAX_DB_RECORDS) {
            int overflowAmount = (int) (totalRecords - MAX_DB_RECORDS);
            int clearedOverflow = experienceRepository.pruneOldestMemories(overflowAmount);
            log.info("Pruned {} overflow records to maintain cap constraint.", clearedOverflow);
        }
    }
}
