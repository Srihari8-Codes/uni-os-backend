package com.unios.controller;

import com.unios.model.Application;
import com.unios.model.Batch;
import com.unios.repository.ApplicationRepository;
import com.unios.repository.BatchRepository;
import com.unios.service.agents.admissions.EligibilityAgent;
import com.unios.service.test.FailureInjectionService;
import com.unios.service.test.StressTestExecutorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stress")
@Slf4j
public class StressTestController {

    private final ApplicationRepository applicationRepository;
    private final BatchRepository batchRepository;
    private final EligibilityAgent eligibilityAgent;
    private final FailureInjectionService failureInjectionService;
    private final StressTestExecutorService stressTestExecutor;

    public StressTestController(ApplicationRepository applicationRepository,
                                BatchRepository batchRepository,
                                EligibilityAgent eligibilityAgent,
                                FailureInjectionService failureInjectionService,
                                StressTestExecutorService stressTestExecutor) {
        this.applicationRepository = applicationRepository;
        this.batchRepository = batchRepository;
        this.eligibilityAgent = eligibilityAgent;
        this.failureInjectionService = failureInjectionService;
        this.stressTestExecutor = stressTestExecutor;
    }

    @PostMapping("/run/full-suite")
    public ResponseEntity<String> runFullSuite(@RequestParam Long batchId) {
        new Thread(() -> stressTestExecutor.runFullSuite(batchId)).start();
        return ResponseEntity.ok("Full QA Stress Suite started. Monitor logs for the final report.");
    }

    @PostMapping("/seed")
    public ResponseEntity<String> seedData(@RequestParam(defaultValue = "1000") int count) {
        log.info("Seeding {} stress applications...", count);
        
        Batch stressBatch = new Batch();
        stressBatch.setName("STRESS_TEST_" + System.currentTimeMillis());
        stressBatch.setStartYear(2026);
        stressBatch.setEndYear(2027);
        stressBatch.setStatus("ACTIVE");
        batchRepository.save(stressBatch);

        List<Application> apps = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            Application app = new Application();
            app.setBatch(stressBatch);
            app.setFullName("Stress Student " + i);
            app.setEmail("stress" + i + "@unios.qa");
            app.setStatus("SUBMITTED");
            app.setDocumentsVerified(true);
            app.setSchoolMarks(50 + (Math.random() * 45));
            app.setNeetScore(150 + (Math.random() * 500));
            app.setAcademicScore(75.0);
            apps.add(app);
            if (i % 100 == 0) {
                applicationRepository.saveAll(apps);
                apps.clear();
            }
        }
        applicationRepository.saveAll(apps);
        return ResponseEntity.ok("Seeded " + count + " applications in batch " + stressBatch.getId());
    }

    @PostMapping("/simulate/llm")
    public void toggleLlm(@RequestParam boolean down) {
        failureInjectionService.setLlmDown(down);
    }

    @PostMapping("/simulate/smtp")
    public void toggleSmtp(@RequestParam boolean down) {
        failureInjectionService.setSmtpDown(down);
    }

    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getMetrics() {
        com.unios.repository.AgentTaskRepository taskRepo = org.springframework.web.context.ContextLoader.getCurrentWebApplicationContext().getBean(com.unios.repository.AgentTaskRepository.class);
        com.unios.repository.FailedTaskRepository failedRepo = org.springframework.web.context.ContextLoader.getCurrentWebApplicationContext().getBean(com.unios.repository.FailedTaskRepository.class);
        com.unios.repository.AgentAuditLogRepository auditRepo = org.springframework.web.context.ContextLoader.getCurrentWebApplicationContext().getBean(com.unios.repository.AgentAuditLogRepository.class);
        com.unios.repository.EmailLogRepository emailRepo = org.springframework.web.context.ContextLoader.getCurrentWebApplicationContext().getBean(com.unios.repository.EmailLogRepository.class);

        Map<String, Object> metrics = new java.util.HashMap<>();
        metrics.put("pendingTasks", taskRepo.findByStatus("PENDING").size());
        metrics.put("processingTasks", taskRepo.findByStatus("PROCESSING").size());
        metrics.put("completedTasks", taskRepo.findByStatus("COMPLETED").size());
        metrics.put("failedTasks", failedRepo.count());
        metrics.put("auditEntries", auditRepo.count());
        metrics.put("emailsSent", emailRepo.findByStatus("SENT").size());
        metrics.put("emailsFailed", emailRepo.findByStatus("FAILED").size());
        return ResponseEntity.ok(metrics);
    }
}
