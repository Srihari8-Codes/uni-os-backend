package com.unios.service.governance;

import com.unios.model.AgentTask;
import com.unios.repository.AgentTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class OverrideDecisionService {

    private final AgentTaskRepository taskRepository;

    /**
     * Records a human override for a specific task.
     */
    @Transactional
    public void provideOverride(String taskId, String action, String reasoning, String paramsJson) {
        log.info("[GOVERNANCE] Recording override for task {}: {}", taskId, action);
        
        AgentTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found: " + taskId));
        
        task.setOverrideAction(action);
        task.setOverrideReasoning(reasoning);
        task.setOverrideParametersJson(paramsJson);
        task.setStatus("PENDING"); // Re-enqueue for processing with the override
        task.setRequiresManualApproval(false); // Clear flag so it proceeds
        
        taskRepository.save(task);
    }

    /**
     * Checks if a task is currently awaiting supervisor approval.
     */
    public boolean isAwaitingApproval(String taskId) {
        return taskRepository.findById(taskId)
                .map(t -> "AWAITING_APPROVAL".equals(t.getStatus()))
                .orElse(false);
    }
}
