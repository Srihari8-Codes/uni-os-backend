package com.unios.service.agents.admissions;

import com.unios.service.agents.tools.ReflectionTool;
import com.unios.service.llm.LLMClient;
import com.unios.service.agents.framework.ToolContext;
import com.unios.service.agents.framework.ToolResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class ReflectionAgent {

    private final ReflectionTool reflectionTool;
    private final LLMClient llmClient;
    private final com.unios.service.EmailService emailService;
    private final com.unios.repository.StrategicLessonRepository strategicLessonRepository;

    public ReflectionAgent(ReflectionTool reflectionTool, LLMClient llmClient, 
                          com.unios.service.EmailService emailService,
                          com.unios.repository.StrategicLessonRepository strategicLessonRepository) {
        this.reflectionTool = reflectionTool;
        this.llmClient = llmClient;
        this.emailService = emailService;
        this.strategicLessonRepository = strategicLessonRepository;
    }

    @Transactional
    public void runPostMortem(Long batchId) {
        System.out.println(">>> [REFLECTION AGENT] Starting Performance Analysis for Batch " + batchId);

        // 1. Gather Data
        ToolResult result = reflectionTool.executeWithContext(
            ToolContext.builder().parameters(Map.of("batchId", batchId)).build()
        );
        String stats = (String) result.getActionData().get("analysisData");

        // 2. Ask AI for Insights
        String prompt = "Analyze the following college admission batch performance data and provide 3 strategic recommendations for the NEXT cycle (e.g. adjust cutoff, increase waitlist, or change seat capacity). BE CONCISE (max 1 sentence per point):\n\n" + stats;
        
        System.out.println(">>> [REFLECTION AGENT] Sending data to AI for Strategic Review...");
        String insights = llmClient.generateResponse(
            "You are a Strategic Enrollment Officer.", 
            prompt
        ); 

        // 3. Save to Strategic Memory
        com.unios.model.StrategicLesson lesson = new com.unios.model.StrategicLesson();
        lesson.setSourceBatchId(batchId);
        lesson.setLesson(insights);
        lesson.setCreatedAt(java.time.LocalDateTime.now());
        strategicLessonRepository.save(lesson);

        // 4. Log and Notify Admin
        String finalReport = "=== ADMISSION CYCLE POST-MORTEM REPORT ===\n\n" + stats + "\n\n=== AI STRATEGIC RECOMMENDATIONS (Saved to Strategic Memory) ===\n" + insights;
        System.out.println(finalReport);

        // Send to Admin for review
        emailService.sendEmail("admin@unios.edu", "University OS: Batch " + batchId + " Performance Analysis", finalReport);
        
        System.out.println("<<< [REFLECTION AGENT] Analysis Complete. Recommendations sent to Board.");
    }
}
