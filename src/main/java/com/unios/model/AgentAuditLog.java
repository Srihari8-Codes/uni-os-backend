package com.unios.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "agent_audit_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentAuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String taskId;
    private String agentName;
    private String entityType;
    private Long entityId;
    
    @Column(length = 2048)
    private String goal;
    
    private String action;
    
    @Column(length = 5000)
    private String reasoning;
    
    @Column(length = 5000)
    private String toolInput;
    
    @Column(length = 5000)
    private String toolOutput;
    
    private double confidence;
    private long executionTimeMs;

    // --- Governance Traceability ---
    @Column(columnDefinition = "TEXT")
    private String fullPrompt;
    
    @Column(columnDefinition = "TEXT")
    private String fullResponse;
    
    private LocalDateTime timestamp;
}
