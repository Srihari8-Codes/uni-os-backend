package com.unios.service.agents.framework;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@Slf4j
public class AgentWorkerPool {

    private final AsyncAgentQueue agentQueue;
    private final AgentExecutionEngine executionEngine;
    private final com.unios.repository.AgentTaskRepository agentTaskRepository;
    private final ExecutorService workerThreads;
    
    @org.springframework.beans.factory.annotation.Value("${unios.agent.pool-size:5}")
    private int poolSize;
    
    @org.springframework.beans.factory.annotation.Value("${unios.agent.retry-max:3}")
    private int maxRetries;

    public AgentWorkerPool(AsyncAgentQueue agentQueue, 
                           AgentExecutionEngine executionEngine,
                           com.unios.repository.AgentTaskRepository agentTaskRepository) {
        this.agentQueue = agentQueue;
        this.executionEngine = executionEngine;
        this.agentTaskRepository = agentTaskRepository;
        // The pool size might not be known yet in constructor if using @Value on field, 
        // but it's okay if we use a default or @PostConstruct.
        this.workerThreads = Executors.newCachedThreadPool(); 
    }

    @PostConstruct
    public void startWorkers() {
        log.info("[WORKER POOL] Initializing {} agent workers...", poolSize);
        for (int i = 0; i < poolSize; i++) {
            workerThreads.submit(this::workerLoop);
        }
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 30000) // Every 30 seconds
    public void recoverPendingTasks() {
        java.util.List<com.unios.model.AgentTask> pendingTasks = agentTaskRepository.findByStatus("PENDING");
        if (!pendingTasks.isEmpty()) {
            log.info("[RECOVERY] Found {} pending tasks in DB. Re-enqueuing for processing.", pendingTasks.size());
            for (com.unios.model.AgentTask entity : pendingTasks) {
                // Re-enqueue without duplicates (simple check for now)
                AgentWorkTask task = AgentWorkTask.builder()
                    .taskId(entity.getTaskId())
                    .agentName(entity.getAgentName())
                    .entityType(entity.getEntityType())
                    .entityId(entity.getEntityId())
                    .goal(entity.getGoal())
                    .context(new java.util.HashMap<>(entity.getContext()))
                    .retryCount(entity.getRetryCount())
                    .status(entity.getStatus())
                    .build();
                agentQueue.enqueue(task);
            }
        }
    }

    private void workerLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            AgentWorkTask task = null;
            try {
                task = agentQueue.dequeue();
                processWithRetry(task);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("[WORKER] Fatal error in worker loop: {}", e.getMessage(), e);
            }
        }
    }

    private void processWithRetry(AgentWorkTask task) {
        int maxRetries = 3;
        long startTime = System.currentTimeMillis();
        
        while (task.getRetryCount() <= maxRetries) {
            try {
                log.info("[WORKER] Processing task {} for agent {} (Attempt {})", 
                        task.getTaskId(), task.getAgentName(), task.getRetryCount() + 1);
                
                agentQueue.updateTaskStatus(task.getTaskId(), "PROCESSING");

                // Execute the agent loop (Planning -> Acting -> Reflecting)
                executionEngine.runLoop(
                    task.getTaskId(),
                    task.getAgentName(), 
                    task.getEntityType(), 
                    task.getEntityId(), 
                    task.getGoal(), 
                    new java.util.HashMap<>(task.getContext()) // Convert to Map<String, Object>
                );
                
                long duration = System.currentTimeMillis() - startTime;
                agentQueue.updateTaskStatus(task.getTaskId(), "COMPLETED");
                log.info("[WORKER] Task {} completed successfully in {}ms.", task.getTaskId(), duration);
                return; // Success, exit retry loop

            } catch (Exception e) {
                task.setRetryCount(task.getRetryCount() + 1);
                long duration = System.currentTimeMillis() - startTime;
                
                if (task.getRetryCount() > maxRetries) {
                    log.error("[WORKER] Task {} failed after {} attempts. Moving to DLQ.", task.getTaskId(), maxRetries);
                    agentQueue.sendToDLQ(task, e.getMessage(), getStackTraceString(e));
                    return;
                }

                // Exponential Backoff: 2^n seconds
                long backoffMillis = (long) Math.pow(2, task.getRetryCount()) * 1000;
                log.warn("[WORKER] Task {} failed (Attempt {}). Retrying in {}ms... Error: {}", 
                        task.getTaskId(), task.getRetryCount(), backoffMillis, e.getMessage());
                
                try {
                    Thread.sleep(backoffMillis);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private String getStackTraceString(Throwable e) {
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }
}
