package com.unios.service.agents.tools;

import com.unios.service.agents.framework.AgentTool;
import com.unios.service.agents.framework.ToolContext;
import com.unios.service.agents.framework.ToolResult;
import com.unios.repository.SlotEnrollmentRepository;
import com.unios.repository.RoomRepository;
import com.unios.repository.BatchRepository;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SystemHealthTool implements AgentTool {

    private final SlotEnrollmentRepository enrollmentRepository;
    private final RoomRepository roomRepository;
    private final BatchRepository batchRepository;

    public SystemHealthTool(SlotEnrollmentRepository enrollmentRepository,
                            RoomRepository roomRepository,
                            BatchRepository batchRepository) {
        this.enrollmentRepository = enrollmentRepository;
        this.roomRepository = roomRepository;
        this.batchRepository = batchRepository;
    }

    @Override
    public String name() {
        return "SystemHealthTool";
    }

    @Override
    public String description() {
        return "Retrieves current institutional metrics (enrollment, capacity, batches). Input: {}";
    }

    @Override
    public ToolResult executeWithContext(ToolContext context) {
        long enrollees = enrollmentRepository.countByStatus("ENROLLED");
        int roomCapacity = roomRepository.findAll().stream().mapToInt(r -> r.getCapacity()).sum();
        long activeBatches = batchRepository.count();
        
        String summary = String.format("Institutional Health: %d/%d seats filled. Active Batches: %d.", 
                             enrollees, roomCapacity, activeBatches);
                             
        return ToolResult.builder()
                .summary(summary)
                .status("SUCCESS")
                .actionData(Map.of("enrollmentCount", enrollees, "roomCapacity", roomCapacity, "activeBatches", activeBatches))
                .confidence(1.0)
                .build();
    }
}
