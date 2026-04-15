package com.unios.service.agents.framework.v5;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationResult {
    private boolean goalAchieved;
    private boolean needsReplan;
    private String analysis;
}
