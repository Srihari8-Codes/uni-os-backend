package com.unios.service.agents.framework.v5.tool.impl;

import com.unios.service.agents.framework.v5.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class WaitlistManager implements Tool {

    @Override
    public String getName() {
        return "WAITLIST_MANAGER";
    }

    @Override
    public String getDescription() {
        return "Promotes waitlisted candidates to fill open seats. Requires 'batchId' and 'openSeats'.";
    }

    @Override
    public String execute(Map<String, Object> input) throws Exception {
        if (!input.containsKey("batchId") || !input.containsKey("openSeats")) {
            throw new IllegalArgumentException("Missing required parameters: 'batchId' and 'openSeats'");
        }

        Long batchId = Long.valueOf(input.get("batchId").toString());
        Integer openSeats = Integer.valueOf(input.get("openSeats").toString());

        log.info("[WAITLIST_MANAGER] Attempting to promote {} waitlisted students for Batch ID {}", openSeats, batchId);

        if (openSeats <= 0) {
            return "No open seats to fill from waitlist.";
        }

        // Simulating DB operations
        int promotedCount = Math.min(openSeats, 45); // e.g. 45 students were actually available
        
        return "Waitlist promotion complete: " + promotedCount + " candidates promoted to ADMITTED status.";
    }

    @Override
    public void rollback(Map<String, Object> input) {
        log.warn("[WAITLIST_MANAGER] Reverting waitlist promotions for batch {}", input.get("batchId"));
        // Demote the promoted students back to WAITLISTED
    }
}
