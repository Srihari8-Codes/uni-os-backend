package com.unios.service.agents.framework;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentWorkTask {
    private String taskId;
    private String agentName;
    private String entityType;
    private Long entityId;
    private String goal;
    private Map<String, String> context;
    private int retryCount;
    private String status; // PENDING, PROCESSING, COMPLETED, FAILED
}
