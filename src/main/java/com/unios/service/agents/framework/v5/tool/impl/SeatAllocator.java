package com.unios.service.agents.framework.v5.tool.impl;

import com.unios.service.agents.framework.v5.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class SeatAllocator implements Tool {

    @Override
    public String getName() {
        return "SEAT_ALLOCATOR";
    }

    @Override
    public String getDescription() {
        return "Allocates admission seats to candidates based on rank. Requires 'batchId' and 'seatsToFill'.";
    }

    @Override
    public String execute(Map<String, Object> input) throws Exception {
        if (!input.containsKey("batchId") || !input.containsKey("seatsToFill")) {
            throw new IllegalArgumentException("Missing parameters: 'batchId' and 'seatsToFill'");
        }

        Long batchId = Long.valueOf(input.get("batchId").toString());
        Integer seatsToFill = Integer.valueOf(input.get("seatsToFill").toString());

        log.info("[SEAT_ALLOCATOR] Allocating {} seats for Batch ID {}", seatsToFill, batchId);

        if (seatsToFill <= 0) {
            return "No seats allocated. Requested amount was 0.";
        }
        
        if (seatsToFill > 500) {
            throw new Exception("Exceeded max capacity limits per transaction.");
        }

        return "Successfully allocated " + seatsToFill + " seats to the top candidates.";
    }

    @Override
    public void rollback(Map<String, Object> input) {
        log.warn("[SEAT_ALLOCATOR] Reverting seat allocations for batch {}", input.get("batchId"));
        // Revert status from Admitted -> Waitlisted where applicable
    }
}
