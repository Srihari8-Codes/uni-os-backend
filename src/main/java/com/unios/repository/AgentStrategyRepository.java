package com.unios.repository;

import com.unios.model.AgentStrategy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AgentStrategyRepository extends JpaRepository<AgentStrategy, Long> {

    @Query("SELECT s FROM AgentStrategy s WHERE s.goalContext = :goalContext AND s.confidenceScore > 0.6 ORDER BY s.confidenceScore DESC LIMIT 3")
    List<AgentStrategy> findTopRelevantStrategies(String goalContext);

    Optional<AgentStrategy> findByGoalContextAndStrategy(String goalContext, String strategy);

}
