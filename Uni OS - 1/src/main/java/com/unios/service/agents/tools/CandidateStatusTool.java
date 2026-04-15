package com.unios.service.agents.tools;

import com.unios.model.Candidate;
import com.unios.repository.CandidateRepository;
import com.unios.service.agents.framework.AgentTool;
import com.unios.service.agents.framework.ToolContext;
import com.unios.service.agents.framework.ToolResult;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class CandidateStatusTool implements AgentTool {

    private final CandidateRepository candidateRepository;

    public CandidateStatusTool(CandidateRepository candidateRepository) {
        this.candidateRepository = candidateRepository;
    }

    @Override
    public String name() {
        return "CandidateStatusTool";
    }

    @Override
    public String description() {
        return "Updates the status of a recruitment candidate. Input: {candidateId: Long, status: String}";
    }

    @Override
    public ToolResult executeWithContext(ToolContext context) {
        Map<String, Object> input = context.getParameters();
        Long candidateId = Long.valueOf(input.get("candidateId").toString());
        String status = input.get("status").toString().toUpperCase();
        
        Candidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new RuntimeException("Candidate not found: " + candidateId));
        
        candidate.setStatus(status);
        candidateRepository.save(candidate);
        
        return ToolResult.builder()
                .summary("Updated candidate " + candidateId + " to status " + status)
                .status("SUCCESS")
                .confidence(1.0)
                .build();
    }
}
