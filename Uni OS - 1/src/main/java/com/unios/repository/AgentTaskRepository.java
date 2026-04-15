package com.unios.repository;

import com.unios.model.AgentTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentTaskRepository extends JpaRepository<AgentTask, String> {
    List<AgentTask> findByStatus(String status);
    List<AgentTask> findByAgentNameAndStatus(String agentName, String status);
    long countByStatus(String status);
}
