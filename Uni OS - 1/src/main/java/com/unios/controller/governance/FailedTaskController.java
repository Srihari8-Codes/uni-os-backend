package com.unios.controller.governance;

import com.unios.model.AgentTask;
import com.unios.repository.AgentTaskRepository;
import com.unios.service.agents.framework.AgentWorkerPool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/governance/failed-tasks")
@RequiredArgsConstructor
@Slf4j
public class FailedTaskController {

    private final AgentTaskRepository taskRepository;
    private final AgentWorkerPool workerPool;

    @PostMapping("/{taskId}/retry")
    public ResponseEntity<String> retryTask(@PathVariable String taskId) {
        AgentTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));
        
        task.setStatus("PENDING");
        task.setRetryCount(task.getRetryCount() + 1);
        taskRepository.save(task);
        
        // Re-enqueue (logic depends on how your worker pool picks up PENDING)
        // Usually the worker pool is a listener or we manually trigger.
        return ResponseEntity.ok("Task " + taskId + " re-queued for retry.");
    }

    @PostMapping("/{taskId}/edit-and-retry")
    public ResponseEntity<String> editAndRetry(@PathVariable String taskId, @RequestBody Map<String, String> contextUpdates) {
        AgentTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));
        
        task.getContext().putAll(contextUpdates);
        task.setStatus("PENDING");
        taskRepository.save(task);
        
        return ResponseEntity.ok("Task " + taskId + " updated and re-queued.");
    }

    @DeleteMapping("/{taskId}")
    public ResponseEntity<String> cancelTask(@PathVariable String taskId) {
        taskRepository.deleteById(taskId);
        return ResponseEntity.ok("Task " + taskId + " cancelled and removed from DLQ.");
    }
}
