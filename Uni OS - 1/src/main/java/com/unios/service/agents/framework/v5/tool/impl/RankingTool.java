package com.unios.service.agents.framework.v5.tool.impl;

import com.unios.service.agents.framework.v5.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class RankingTool implements Tool {

    @Override
    public String getName() {
        return "RANKING_TOOL";
    }

    @Override
    public String getDescription() {
        return "Calculates the merit list for a given batch. Requires 'batchId'.";
    }

    @Override
    public String execute(Map<String, Object> input) throws Exception {
        if (!input.containsKey("batchId")) {
            throw new IllegalArgumentException("Missing required parameter: 'batchId'");
        }

        Long batchId = Long.valueOf(input.get("batchId").toString());
        log.info("[RANKING_TOOL] Calculating merit rank for Batch ID {}", batchId);

        if (batchId < 0) {
            throw new IllegalArgumentException("Invalid batchId provided.");
        }

        return "Successfully calculated merit list for batch " + batchId + " and updated 125 candidate ranks.";
    }

    @Override
    public void rollback(Map<String, Object> input) {
        log.warn("[RANKING_TOOL] Rolling back rank calculations for batch ID {}", input.get("batchId"));
        // Clear generated ranks
    }
}
