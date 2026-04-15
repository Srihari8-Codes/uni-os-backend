package com.unios.service.agents.tools;

import com.unios.service.agents.admissions.EnrollmentAgent;
import com.unios.service.agents.framework.AgentTool;
import com.unios.service.agents.framework.ToolContext;
import com.unios.service.agents.framework.ToolResult;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class EnrollmentTool implements AgentTool {

    private final EnrollmentAgent enrollmentAgent;

    public EnrollmentTool(EnrollmentAgent enrollmentAgent) {
        this.enrollmentAgent = enrollmentAgent;
    }

    @Override
    public String name() {
        return "EnrollmentTool";
    }

    @Override
    public String description() {
        return "Converts a passed applicant into a registered Student and creates their user credentials. Input: {applicationId: Long}";
    }

    @Override
    public ToolResult executeWithContext(ToolContext context) {
        Map<String, Object> input = context.getParameters();
        Long applicationId = Long.valueOf(input.get("applicationId").toString());
        String department = input.getOrDefault("department", "General").toString();
        Double fees = Double.valueOf(input.getOrDefault("fees", "50000.0").toString());
        String notes = input.getOrDefault("notes", "Agent automated enrollment").toString();
        
        Object result = enrollmentAgent.enrollSingle(applicationId, department, fees, notes);
        
        return ToolResult.builder()
                .summary("Student enrolled successfully for application " + applicationId)
                .status("SUCCESS")
                .actionData(Map.of("result", result != null ? result.toString() : "Enrollment complete"))
                .confidence(1.0)
                .build();
    }
}
