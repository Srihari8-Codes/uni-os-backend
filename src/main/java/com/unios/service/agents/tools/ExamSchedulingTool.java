package com.unios.service.agents.tools;

import com.unios.model.Batch;
import com.unios.repository.BatchRepository;
import com.unios.service.agents.admissions.ExamSchedulerAgent;
import com.unios.service.agents.framework.AgentTool;
import com.unios.service.agents.framework.ToolContext;
import com.unios.service.agents.framework.ToolResult;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ExamSchedulingTool implements AgentTool {

    private final ExamSchedulerAgent examSchedulerAgent;
    private final BatchRepository batchRepository;

    public ExamSchedulingTool(ExamSchedulerAgent examSchedulerAgent, BatchRepository batchRepository) {
        this.examSchedulerAgent = examSchedulerAgent;
        this.batchRepository = batchRepository;
    }

    @Override
    public String name() {
        return "ExamSchedulingTool";
    }

    @Override
    public String description() {
        return "Optimizes exam hall allocations for a batch using constraint-based scheduling. Input: {batchId: Long}";
    }

    @Override
    public ToolResult executeWithContext(ToolContext context) {
        Map<String, Object> input = context.getParameters();
        Long batchId = Long.valueOf(input.get("batchId").toString());
        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new RuntimeException("Batch not found for ID: " + batchId));
        
        com.unios.model.ExamSchedule schedule = examSchedulerAgent.generateSchedule(batchId, batch);
        
        return ToolResult.builder()
                .summary("Exam schedule generated for batch " + batchId)
                .status("SUCCESS")
                .actionData(Map.of("schedule", schedule.getHallAllocations()))
                .confidence(1.0)
                .build();
    }
}
