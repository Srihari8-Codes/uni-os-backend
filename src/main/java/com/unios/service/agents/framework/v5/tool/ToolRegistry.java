package com.unios.service.agents.framework.v5.tool;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class ToolRegistry {

    private final List<Tool> registeredTools;
    private final Map<String, Tool> toolMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        if (registeredTools != null) {
            for (Tool tool : registeredTools) {
                toolMap.put(tool.getName(), tool);
                log.info("Registered Tool: {}", tool.getName());
            }
        }
    }

    public List<Tool> getRegisteredTools() {
        return registeredTools;
    }

    public Tool getTool(String name) {
        return toolMap.get(name);
    }
}
