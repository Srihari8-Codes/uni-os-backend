package com.unios.service.agents.tools;

import com.unios.service.agents.framework.AgentTool;
import com.unios.service.agents.framework.ToolContext;
import com.unios.service.agents.framework.ToolResult;
import com.unios.service.EmailService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class EmailTriggerTool implements AgentTool {

    private final EmailService emailService;

    public EmailTriggerTool(EmailService emailService) {
        this.emailService = emailService;
    }

    @Override
    public String name() {
        return "EmailTriggerTool";
    }

    @Override
    public String description() {
        return "Sends an email notification to a recipient. Input: {to: String, subject: String, body: String}";
    }

    @Override
    public ToolResult executeWithContext(ToolContext context) {
        Map<String, Object> input = context.getParameters();
        String to = input.get("to").toString();
        String subject = input.get("subject").toString();
        String body = input.get("body").toString();
        
        emailService.sendEmail(to, subject, body);
        return ToolResult.builder()
                .summary("Email queued for delivery to " + to)
                .status("SUCCESS")
                .confidence(1.0)
                .build();
    }
}
