package com.unios.service.agents.tools;

import com.unios.service.agents.admissions.RankingAgent;
import com.unios.service.agents.framework.AgentTool;
import com.unios.service.agents.framework.ToolContext;
import com.unios.service.agents.framework.ToolResult;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component("legacyRankingTool")
public class RankingTool implements AgentTool {

    private final RankingAgent rankingAgent;
    private final com.unios.repository.ApplicationRepository applicationRepository;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    public RankingTool(RankingAgent rankingAgent,
                       com.unios.repository.ApplicationRepository applicationRepository,
                       org.springframework.context.ApplicationEventPublisher eventPublisher) {
        this.rankingAgent = rankingAgent;
        this.applicationRepository = applicationRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public String name() {
        return "RankingTool";
    }

    @Override
    public String description() {
        return "Processes entrance exam results for a batch and updates student statuses (PASSED/WAITLISTED/FAILED). Input: {batchId: Long}";
    }

    @Override
    public ToolResult executeWithContext(ToolContext context) {
        Map<String, Object> input = context.getParameters();
        if (input.containsKey("applicationId") && input.containsKey("status")) {
            Long appId = Long.valueOf(input.get("applicationId").toString());
            String status = input.get("status").toString().toUpperCase();
            
            com.unios.model.Application app = applicationRepository.findById(appId).orElse(null);
            if (app != null) {
                app.setStatus(status);
                applicationRepository.save(app);
                
                if (status.equals("EXAM_PASSED")) {
                    eventPublisher.publishEvent(new com.unios.domain.events.ApplicantPassedEvent(this, appId));
                }
                return ToolResult.builder()
                        .summary("Updated application " + appId + " to " + status)
                        .status("SUCCESS")
                        .confidence(1.0)
                        .build();
            }
            return ToolResult.builder()
                    .summary("Application " + appId + " not found.")
                    .status("FAILED")
                    .build();
        }
        
        Long batchId = Long.valueOf(input.get("batchId").toString());
        rankingAgent.processResults(batchId);
        return ToolResult.builder()
                .summary("Ranking processed for batch " + batchId)
                .status("SUCCESS")
                .confidence(1.0)
                .build();
    }
}
