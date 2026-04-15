package com.unios.service.agents.framework.v5.tool.impl;

import com.unios.service.agents.framework.v5.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class NotificationTool implements Tool {

    @Override
    public String getName() {
        return "NOTIFICATION_TOOL";
    }

    @Override
    public String getDescription() {
        return "Dispatches communications (email/SMS/voice). Requires 'recipientId', 'messageType', and 'content'.";
    }

    @Override
    public String execute(Map<String, Object> input) throws Exception {
        if (!input.containsKey("recipientId") || !input.containsKey("messageType") || !input.containsKey("content")) {
            throw new IllegalArgumentException("Missing required parameters: 'recipientId', 'messageType', and 'content'");
        }

        String recipientId = input.get("recipientId").toString();
        String type = input.get("messageType").toString();
        String content = input.get("content").toString();

        log.info("[NOTIFICATION_TOOL] Dispatching {} to {}: {}", type, recipientId, content);

        if (content.isBlank()) {
            throw new Exception("Message content cannot be empty.");
        }

        return "Notification successfully dispatched to " + recipientId + " via " + type + ".";
    }

    @Override
    public void rollback(Map<String, Object> input) {
        log.warn("[NOTIFICATION_TOOL] Cannot strictly rollback sent messages. Logging cancellation event for {}.", input.get("recipientId"));
        // Could potentially send a follow-up correction if system supports it
    }
}
