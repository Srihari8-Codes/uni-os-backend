package com.unios.repository;

import com.unios.model.AgentExperience;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AgentExperienceRepository extends JpaRepository<AgentExperience, Long> {

    List<AgentExperience> findByGoalIdOrderByScoreDesc(String goalId);

    @Query("SELECT e FROM AgentExperience e WHERE e.action = :action AND e.score > 0.5 ORDER BY e.score DESC LIMIT 5")
    List<AgentExperience> findTopRelevantByAction(String action);

    Optional<AgentExperience> findFirstByGoalIdAndActionAndOutcome(String goalId, String action, String outcome);

    @Modifying
    @Query("DELETE FROM AgentExperience e WHERE e.score < :threshold AND e.timestamp < :cutoffDate")
    int pruneLowScoringMemories(Double threshold, LocalDateTime cutoffDate);

    @Modifying
    @Query(value = "DELETE FROM agent_experiences WHERE id IN " +
            "(SELECT id FROM agent_experiences ORDER BY timestamp ASC LIMIT :limit)", nativeQuery = true)
    int pruneOldestMemories(int limit);
}
