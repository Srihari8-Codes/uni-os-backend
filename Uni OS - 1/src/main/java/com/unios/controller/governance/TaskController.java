package com.unios.controller.governance;

import com.unios.model.AgentAuditLog;
import com.unios.model.AgentTask;
import com.unios.repository.AgentAuditLogRepository;
import com.unios.repository.AgentTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/governance/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final AgentTaskRepository taskRepository;
    private final AgentAuditLogRepository auditLogRepository;

    @GetMapping
    public List<AgentTask> getAllTasks() {
        return taskRepository.findAll();
    }

    @GetMapping("/{taskId}/trace")
    public List<AgentAuditLog> getTaskTrace(@PathVariable String taskId) {
        return auditLogRepository.findByTaskId(taskId);
    }

    @GetMapping("/stats")
    public Map<String, Long> getStats() {
        return Map.of(
            "total", taskRepository.count(),
            "pending", (long) taskRepository.findByStatus("PENDING").size(),
            "awaiting", (long) taskRepository.findByStatus("AWAITING_APPROVAL").size(),
            "completed", (long) taskRepository.findByStatus("COMPLETED").size(),
            "failed", (long) taskRepository.findByStatus("FAILED").size()
        );
    }
}
