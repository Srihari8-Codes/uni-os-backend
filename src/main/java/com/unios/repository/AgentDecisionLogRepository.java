package com.unios.repository;

import com.unios.model.AgentDecisionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AgentDecisionLogRepository extends JpaRepository<AgentDecisionLog, Long> {
    java.util.List<AgentDecisionLog> findByEntityTypeAndEntityId(String entityType, Long entityId);
}
