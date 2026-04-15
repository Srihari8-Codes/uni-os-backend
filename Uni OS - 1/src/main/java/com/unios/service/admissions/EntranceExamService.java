package com.unios.service.admissions;

import com.unios.model.Application;
import com.unios.model.ExamResult;
import com.unios.repository.ApplicationRepository;
import com.unios.repository.BatchRepository;
import com.unios.repository.ExamResultRepository;
import com.unios.service.agents.admissions.RankingAgent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.unios.domain.events.ApplicantPassedEvent;

import java.util.*;

@Service
public class EntranceExamService {

    private final ApplicationRepository applicationRepository;
    private final ExamResultRepository examResultRepository;
    private final BatchRepository batchRepository;
    private final RankingAgent rankingAgent;
    private final ApplicationEventPublisher eventPublisher;

    public EntranceExamService(ApplicationRepository applicationRepository,
                              ExamResultRepository examResultRepository,
                              BatchRepository batchRepository,
                              RankingAgent rankingAgent,
                              ApplicationEventPublisher eventPublisher) {
        this.applicationRepository = applicationRepository;
        this.examResultRepository = examResultRepository;
        this.batchRepository = batchRepository;
        this.rankingAgent = rankingAgent;
        this.eventPublisher = eventPublisher;
    }

    public static class Question {
        public String id;
        public String text;
        public List<String> options;
        public String correctAnswer;

        public Question(String id, String text, String optA, String optB, String optC, String optD, String correct) {
            this.id = id;
            this.text = text;
            this.options = Arrays.asList(optA, optB, optC, optD);
            this.correctAnswer = correct;
        }
    }

    private final List<Question> ALL_QUESTIONS = Arrays.asList(
        new Question("1", "Which data structure uses LIFO?", "Queue", "Stack", "Array", "Linked List", "Stack"),
        new Question("2", "What is the time complexity of binary search?", "O(n)", "O(log n)", "O(1)", "O(n log n)", "O(log n)"),
        new Question("3", "What does SQL stand for?", "Simple Query Lang", "Structured Query Lang", "Sequential Query Lang", "Standard Query Lang", "Structured Query Lang"),
        new Question("4", "Which operator is used for comparison in Java?", "=", "==", "===", "!=", "=="),
        new Question("5", "What is the size of 'int' in Java?", "16-bit", "32-bit", "64-bit", "8-bit", "32-bit"),
        new Question("6", "Which keyword is used to inherit a class?", "this", "super", "extends", "implements", "extends"),
        new Question("7", "Process of wrapping data and methods into a single unit is?", "Inheritance", "Polymorphism", "Abstraction", "Encapsulation", "Encapsulation"),
        new Question("8", "What is the default value of a local variable in Java?", "0", "null", "No default", "Depends on type", "No default"),
        new Question("9", "Which of these is NOT a primitive type?", "int", "char", "Boolean", "double", "Boolean"),
        new Question("10", "Which method is the entry point for Java applications?", "start()", "init()", "main()", "execute()", "main()"),
        new Question("11", "Complexity of adding an element to the end of an ArrayList?", "O(1)", "O(n)", "O(log n)", "O(n log n)", "O(1)"),
        new Question("12", "Which protocol is stateless?", "TCP", "UDP", "HTTP", "FTP", "HTTP")
    );

    public Application login(String appIdRaw, String password) {
        Application app;
        if (appIdRaw.contains("@")) {
            List<Application> apps = applicationRepository.findByBatchIdAndStatus(null, null); // Just for reference
            // Use findFirstByEmailAndStatusInOrderByCreatedAtDesc or similar
            // Since the repository doesn't have it exactly, I'll use findByEmail and filter here or add a new repo method
            // Actually I'll use findFirstByEmailAndStatus as it's already in the repo
            app = applicationRepository.findFirstByEmailAndStatus(appIdRaw.trim(), "EXAM_SCHEDULED")
                    .or(() -> applicationRepository.findFirstByEmailAndStatus(appIdRaw.trim(), "EXAM_IN_PROGRESS"))
                    .or(() -> applicationRepository.findByEmail(appIdRaw.trim())) // Fallback to unique if possible
                    .orElseThrow(() -> new RuntimeException("Application with this email not found or not currently scheduled for an exam."));
        } else {
            Long id = parseAppId(appIdRaw);
            app = applicationRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Application ID " + appIdRaw + " not found."));
        }

        if (app.getExamPassword() == null || !app.getExamPassword().trim().equals(password.trim())) {
            throw new RuntimeException("Invalid Password.");
        }

        // Strict guards to prevent re-entry after disqualification or completion
        if ("EXAM_FAILED".equals(app.getStatus())) {
            throw new RuntimeException("LOGIN DENIED: You have been disqualified from this exam session due to an integrity violation.");
        }

        if ("EXAM_COMPLETED".equals(app.getStatus()) || "EXAM_PASSED".equals(app.getStatus())) {
            throw new RuntimeException("LOGIN DENIED: You have already submitted your results for this exam session.");
        }

        // Set status to EXAM_IN_PROGRESS to prevent multiple logins and maintain integrity
        app.setStatus("EXAM_IN_PROGRESS");
        applicationRepository.save(app);

        return app;
    }

    private Long parseAppId(String raw) {
        try {
            return Long.parseLong(raw.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            throw new RuntimeException("Invalid Application ID format.");
        }
    }

    public List<Question> getQuestions() {
        List<Question> shuffled = new ArrayList<>(ALL_QUESTIONS);
        Collections.shuffle(shuffled);
        return shuffled.subList(0, 10);
    }

    @Transactional
    public Map<String, Object> submitExam(String appIdRaw, Map<String, String> answers) {
        Long id = parseAppId(appIdRaw);
        Application app = applicationRepository.findById(id).orElseThrow();

        int correctCount = 0;
        for (Question q : ALL_QUESTIONS) {
            if (answers.containsKey(q.id) && q.correctAnswer.equals(answers.get(q.id))) {
                correctCount++;
            }
        }

        double score = (correctCount / 10.0) * 100.0;

        // Save result
        ExamResult result = examResultRepository.findByApplicationId(id).orElse(new ExamResult());
        result.setApplication(app);
        result.setScore(score);
        result.setRank(0); // Agent will compute eventually if needed
        examResultRepository.save(result);

        // Update Application Status to COMPLETED first
        app.setStatus("EXAM_COMPLETED");
        applicationRepository.save(app);

        // Check if all students in this batch have completed the exam
        Long batchId = app.getBatch().getId();
        long stillScheduled = applicationRepository.countByBatchIdAndStatus(batchId, "EXAM_SCHEDULED");
        long stillInProgress = applicationRepository.countByBatchIdAndStatus(batchId, "EXAM_IN_PROGRESS");

        if (stillScheduled == 0 && stillInProgress == 0) {
            System.out.println("[ENTRANCE EXAM] All students finished. Triggering Batch Ranking for Batch: " + batchId);
            rankingAgent.processResults(batchId);
        } else {
            System.out.println("[ENTRANCE EXAM] " + (stillScheduled + stillInProgress) + " students still pending. Waiting for completion.");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("score", score);
        response.put("status", "EXAM_COMPLETED");
        response.put("correctCount", correctCount);
        return response;
    }
}