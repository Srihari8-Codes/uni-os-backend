package com.unios.service.agents.tools;

import com.unios.model.Application;
import com.unios.repository.ApplicationRepository;
import com.unios.service.agents.framework.AgentTool;
import com.unios.service.agents.framework.ToolContext;
import com.unios.service.agents.framework.ToolResult;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ApplicationStatusTool implements AgentTool {

    private final ApplicationRepository applicationRepository;

    public ApplicationStatusTool(ApplicationRepository applicationRepository) {
        this.applicationRepository = applicationRepository;
    }

    @Override
    public String name() {
        return "ApplicationStatusTool";
    }

    @Override
    public String description() {
        return "Updates the status of a specific application. Input: {applicationId: Long, status: String}";
    }

    @Override
    public ToolResult executeWithContext(ToolContext context) {
        Map<String, Object> input = context.getParameters();
        Long applicationId = Long.valueOf(input.get("applicationId").toString());
        String status = input.get("status").toString();
        
        Application app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new RuntimeException("Application not found: " + applicationId));
        
        app.setStatus(status);
        applicationRepository.save(app);
        
        return ToolResult.builder()
                .summary("Updated application " + applicationId + " to status " + status)
                .status("SUCCESS")
                .confidence(1.0)
                .build();
    }
}
