package com.unios.service.agents.framework.v5;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentPlan {
    private String toolName;
    private Map<String, Object> parameters;
    private String reasoning;
    private boolean complete;
}
