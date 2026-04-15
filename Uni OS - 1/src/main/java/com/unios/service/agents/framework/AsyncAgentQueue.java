package com.unios.service.agents.framework;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Service
@Slf4j
public class AsyncAgentQueue {

    private final BlockingQueue<AgentWorkTask> queue;
    private final com.unios.repository.AgentTaskRepository agentTaskRepository;
    private final com.unios.repository.FailedTaskRepository failedTaskRepository;

    public AsyncAgentQueue(@org.springframework.beans.factory.annotation.Value("${unios.agent.queue-capacity:1000}") int capacity,
                           com.unios.repository.AgentTaskRepository agentTaskRepository,
                           com.unios.repository.FailedTaskRepository failedTaskRepository) {
        this.queue = new LinkedBlockingQueue<>(capacity);
        this.agentTaskRepository = agentTaskRepository;
        this.failedTaskRepository = failedTaskRepository;
    }

    public void enqueue(AgentWorkTask task) {
        log.info("[QUEUE] Enqueuing task {} for agent {}", task.getTaskId(), task.getAgentName());
        
        // Always persist to DB first for durability
        persistTask(task, "PENDING");

        if (!queue.offer(task)) {
            log.warn("[QUEUE] Main queue is FULL! Task {} is safely persisted in DB as PENDING.", task.getTaskId());
            // It stays in DB as PENDING, a scheduler could pick it up later if the queue clears.
        }
    }

    public AgentWorkTask dequeue() throws InterruptedException {
        return queue.take();
    }

    public void sendToDLQ(AgentWorkTask task, String errorMessage, String stackTrace) {
        log.error("[QUEUE] Task {} moved to Persistent DLQ. Reason: {}", task.getTaskId(), errorMessage);
        
        com.unios.model.FailedTask failedTask = com.unios.model.FailedTask.builder()
                .taskId(task.getTaskId())
                .agentName(task.getAgentName())
                .entityType(task.getEntityType())
                .entityId(task.getEntityId())
                .errorMessage(errorMessage)
                .stackTrace(stackTrace)
                .build();
        
        failedTaskRepository.save(failedTask);
        updateTaskStatus(task.getTaskId(), "FAILED");
    }

    private void persistTask(AgentWorkTask task, String status) {
        Map<String, String> stringContext = new HashMap<>();
        if (task.getContext() != null) {
            task.getContext().forEach((k, v) -> stringContext.put(k, String.valueOf(v)));
        }

        com.unios.model.AgentTask entity = com.unios.model.AgentTask.builder()
                .taskId(task.getTaskId())
                .agentName(task.getAgentName())
                .entityType(task.getEntityType())
                .entityId(task.getEntityId())
                .goal(task.getGoal())
                .context(stringContext)
                .retryCount(task.getRetryCount())
                .status(status)
                .build();
        
        agentTaskRepository.save(entity);
    }

    public void updateTaskStatus(String taskId, String status) {
        agentTaskRepository.findById(taskId).ifPresent(t -> {
            t.setStatus(status);
            agentTaskRepository.save(t);
        });
    }
}
