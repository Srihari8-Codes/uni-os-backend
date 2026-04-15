package com.unios.service.agents.academics;

import com.unios.model.*;
import com.unios.repository.*;
import com.unios.service.llm.ResilientLLMService;
import com.unios.dto.DecisionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

@Service
@Slf4j
public class CourseExamSchedulerAgent {

    private final SubjectOfferingRepository subjectOfferingRepository;
    private final ExamHallRepository examHallRepository;
    private final CourseExamRepository courseExamRepository;
    private final ResilientLLMService llmService;

    public CourseExamSchedulerAgent(SubjectOfferingRepository subjectOfferingRepository,
                                    ExamHallRepository examHallRepository,
                                    CourseExamRepository courseExamRepository,
                                    ResilientLLMService llmService) {
        this.subjectOfferingRepository = subjectOfferingRepository;
        this.examHallRepository = examHallRepository;
        this.courseExamRepository = courseExamRepository;
        this.llmService = llmService;
    }

    @Transactional
    public void schedulePendingExams() {
        log.info("[EXAM SCHEDULER] Scanning for subjects pending examination...");
        
        List<SubjectOffering> pending = subjectOfferingRepository.findByStatus("EXAM_PENDING");
        if (pending.isEmpty()) {
            log.info("[EXAM SCHEDULER] No subjects pending examination.");
            return;
        }

        List<ExamHall> halls = examHallRepository.findAll();
        if (halls.isEmpty()) {
            log.error("[EXAM SCHEDULER] No exam halls found in database!");
            return;
        }

        Random random = new Random();
        for (SubjectOffering subject : pending) {
            if (courseExamRepository.findBySubjectOfferingId(subject.getId()).isPresent()) {
                continue; // Already scheduled
            }

            // 1. Assign random hall (simplification: no conflict check for now)
            ExamHall hall = halls.get(random.nextInt(halls.size()));

            // 2. Assign time (Fixed: next Monday at 10 AM)
            LocalDateTime examTime = LocalDateTime.now().plusWeeks(1).withHour(10).withMinute(0).withSecond(0).withNano(0);

            // 3. Generate AI questions
            String questionsJson = generateAIQuestions(subject.getSubjectName());

            // 4. Create CourseExam
            CourseExam exam = new CourseExam();
            exam.setSubjectOffering(subject);
            exam.setExamHall(hall);
            exam.setStartTime(examTime);
            exam.setQuestionsJson(questionsJson);
            exam.setStatus("SCHEDULED");
            courseExamRepository.save(exam);

            // 5. Update Subject Offering
            subject.setStatus("EXAM_SCHEDULED");
            subject.setLockedForExam(true);
            subjectOfferingRepository.save(subject);

            log.info("[EXAM SCHEDULER] Scheduled: {} | Hall: {} | Time: {}", 
                subject.getSubjectName(), hall.getName(), examTime);
        }
    }

    private String generateAIQuestions(String subjectName) {
        log.info("[AI EXAM] Generating questions for: {}", subjectName);
        String systemPrompt = "You are a university professor for " + subjectName + ". Generate a formal 10-question MCQ exam in JSON format.";
        String userPrompt = "Generate 10 multiple choice questions. Each question must have: 'question', 'options' (list of 4 strings), and 'correctIndex' (0-3). Output ONLY the raw JSON array of 10 objects.";
        
        DecisionResponse dr = llmService.executeWithResilience(systemPrompt, userPrompt);
        // The reasoning field contains the string response from the LLM
        return dr.getReasoning(); 
    }
}
