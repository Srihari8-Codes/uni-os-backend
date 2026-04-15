package com.unios.repository;

import com.unios.model.AgentActionOutcome;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentActionOutcomeRepository extends JpaRepository<AgentActionOutcome, Long> {

    List<AgentActionOutcome> findByToolNameOrderByTimestampDesc(String toolName);

    /**
     * Get average effectiveness per tool.
     */
    @Query("SELECT a.toolName, AVG(a.effectivenessScore) FROM AgentActionOutcome a GROUP BY a.toolName")
    List<Object[]> getAverageEffectivenessPerTool();

    /**
     * Get recent failures for a specific tool.
     */
    @Query("SELECT COUNT(a) FROM AgentActionOutcome a WHERE a.toolName = :toolName AND a.status = 'FAILED' AND a.timestamp > :since")
    long countRecentFailures(String toolName, java.time.LocalDateTime since);
}
