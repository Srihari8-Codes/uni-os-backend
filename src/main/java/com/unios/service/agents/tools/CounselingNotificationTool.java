package com.unios.service.agents.tools;

import com.unios.service.agents.framework.AgentTool;
import com.unios.service.agents.framework.ToolContext;
import com.unios.service.agents.framework.ToolResult;
import com.unios.service.EmailService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class CounselingNotificationTool implements AgentTool {

    private final EmailService emailService;

    public CounselingNotificationTool(EmailService emailService) {
        this.emailService = emailService;
    }

    @Override
    public String name() {
        return "CounselingNotificationTool";
    }

    @Override
    public String description() {
        return "Sends an email notification to a recipient. Input: {to: String, applicantName: String, time: String}";
    }

    @Override
    public ToolResult executeWithContext(ToolContext context) {
        Map<String, Object> input = context.getParameters();
        String to = input.get("to").toString();
        String appName = input.get("applicantName").toString();
        String time = input.get("time").toString();
        
        emailService.sendEmail(to, "Counseling Session Scheduled", 
            "Dear " + appName + ", your counseling session is scheduled at " + time);
        
        return ToolResult.builder()
                .summary("Counseling notification sent to " + to)
                .status("SUCCESS")
                .confidence(1.0)
                .build();
    }
}
