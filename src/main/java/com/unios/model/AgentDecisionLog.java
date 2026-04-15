package com.unios.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "agent_decision_logs")
@Data
@NoArgsConstructor
public class AgentDecisionLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String agentName;
    private String entityType; // Application, Student, Candidate
    private Long entityId;

    @Column(length = 5000)
    private String decision; // The full output or summary

    // Optional: Reasoning or detailed prompt response
    @Column(length = 5000)
    private String reasoning;

    private LocalDateTime timestamp;

    public AgentDecisionLog(String agentName, String entityType, Long entityId, String decision, String reasoning) {
        this.agentName = agentName;
        this.entityType = entityType;
        this.entityId = entityId;
        this.decision = decision;
        this.reasoning = reasoning;
        this.timestamp = LocalDateTime.now();
    }
}
