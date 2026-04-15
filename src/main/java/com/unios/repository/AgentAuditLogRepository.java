package com.unios.repository;

import com.unios.model.AgentAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentAuditLogRepository extends JpaRepository<AgentAuditLog, Long> {
    List<AgentAuditLog> findByTaskId(String taskId);
    List<AgentAuditLog> findByEntityTypeAndEntityId(String entityType, Long entityId);
}
