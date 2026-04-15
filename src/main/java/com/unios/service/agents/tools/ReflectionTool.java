package com.unios.service.agents.tools;

import com.unios.service.agents.framework.AgentTool;
import com.unios.service.agents.framework.ToolContext;
import com.unios.service.agents.framework.ToolResult;
import com.unios.repository.ApplicationRepository;
import com.unios.repository.BatchRepository;
import com.unios.model.Batch;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.List;

@Component
public class ReflectionTool implements AgentTool {

    private final ApplicationRepository applicationRepository;
    private final BatchRepository batchRepository;

    public ReflectionTool(ApplicationRepository applicationRepository, BatchRepository batchRepository) {
        this.applicationRepository = applicationRepository;
        this.batchRepository = batchRepository;
    }

    @Override
    public String name() {
        return "ReflectionTool";
    }

    @Override
    public String description() {
        return "Analyzes performance of a completed batch. Input: {batchId: Long}";
    }

    @Override
    public ToolResult executeWithContext(ToolContext context) {
        Long batchId = Long.parseLong(context.getParameters().get("batchId").toString());
        Batch batch = batchRepository.findById(batchId).orElseThrow();

        long enrolled = applicationRepository.countByBatchIdAndStatus(batchId, "ENROLLED");
        long rejected = applicationRepository.countByBatchIdAndStatus(batchId, "REJECTED");
        long failed = applicationRepository.countByBatchIdAndStatus(batchId, "EXAM_FAILED");
        long totalApplicants = applicationRepository.countByBatchId(batchId);

        double fillRate = batch.getSeatCapacity() == 0 ? 0 : (double) enrolled / batch.getSeatCapacity() * 100.0;
        
        String analysis = String.format(
            "Batch: %s\n" +
            "Capacity: %d, Enrolled: %d (Fill Rate: %.1f%%)\n" +
            "Total Applicants: %d\n" +
            "Failed Entrance: %d\n" +
            "Waitlist/Counsellor Rejections: %d\n",
            batch.getName(), batch.getSeatCapacity(), enrolled, fillRate, totalApplicants, failed, rejected
        );

        return ToolResult.builder()
                .summary("Gathered performance data for batch " + batchId)
                .status("SUCCESS")
                .actionData(Map.of("analysisData", analysis, "fillRate", fillRate))
                .confidence(1.0)
                .build();
    }
}
