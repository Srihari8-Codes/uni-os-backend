package com.unios.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "agent_tasks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTask {

    @Id
    private String taskId;

    private String agentName;
    private String entityType;
    private Long entityId;

    @Column(length = 2048)
    private String goal;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "agent_task_context", joinColumns = @JoinColumn(name = "task_id"))
    @MapKeyColumn(name = "context_key")
    @Column(name = "context_value", length = 2048)
    private Map<String, String> context;

    @Builder.Default
    private Integer retryCount = 0;

    private String status; // PENDING, PROCESSING, COMPLETED, FAILED, AWAITING_APPROVAL

    // --- Governance Fields ---
    @Builder.Default
    private Boolean requiresManualApproval = false;
    private String overrideAction;
    private String overrideReasoning;
    
    @Column(columnDefinition = "TEXT")
    private String overrideParametersJson;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
    private String errorMessage;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
