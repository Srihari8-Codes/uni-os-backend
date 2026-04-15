package com.unios.service.agents.admissions;

import com.unios.model.Application;
import com.unios.model.ExamResult;
import com.unios.model.Batch;
import com.unios.repository.ApplicationRepository;
import com.unios.repository.BatchRepository;
import com.unios.repository.ExamResultRepository;
import com.unios.service.marks.MarksPullService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RankingAgent {

    private final ApplicationRepository applicationRepository;
    private final BatchRepository batchRepository;
    private final com.unios.repository.CounselingSessionRepository counselingSessionRepository;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final com.unios.service.EmailService emailService;
    private final ExamResultRepository examResultRepository;
    private final com.unios.service.agents.framework.AsyncAgentQueue asyncQueue;
    private final MarksPullService marksPullService;
    private final com.unios.service.admissions.ScoringService scoringService;

    public RankingAgent(ApplicationRepository applicationRepository,
            BatchRepository batchRepository,
            com.unios.repository.CounselingSessionRepository counselingSessionRepository,
            org.springframework.context.ApplicationEventPublisher eventPublisher,
            com.unios.service.EmailService emailService,
            ExamResultRepository examResultRepository,
            MarksPullService marksPullService,
            com.unios.service.agents.framework.AsyncAgentQueue asyncQueue,
            com.unios.service.admissions.ScoringService scoringService) {
        this.applicationRepository = applicationRepository;
        this.batchRepository = batchRepository;
        this.counselingSessionRepository = counselingSessionRepository;
        this.eventPublisher = eventPublisher;
        this.emailService = emailService;
        this.examResultRepository = examResultRepository;
        this.marksPullService = marksPullService;
        this.asyncQueue = asyncQueue;
        this.scoringService = scoringService;
    }

    @Transactional
    public void processResults(Long batchId) {
        // High-level check: are there any students still in the exam phase?
        long pendingCount = applicationRepository.countByBatchIdAndStatusIn(batchId, 
                List.of("EXAM_SCHEDULED", "EXAM_IN_PROGRESS"));

        if (pendingCount > 0) {
            System.out.println("[RANKING AGENT] Batch " + batchId + " has " + pendingCount + " students still in exam. Skipping ranking.");
            return;
        }

        // Only pull marks if absolutely necessary (e.g., external scores), 
        // normally EntranceExamService has already populated ExamResult.
        Map<Long, Double> pulledMarks = marksPullService.pullMarksForBatch(batchId);

        for (Map.Entry<Long, Double> entry : pulledMarks.entrySet()) {
            Application app = applicationRepository.findById(entry.getKey()).orElse(null);
            if (app == null) continue;

            ExamResult result = examResultRepository.findByApplicationId(entry.getKey())
                    .orElse(new ExamResult());
            result.setApplication(app);
            result.setScore(entry.getValue());
            if (result.getRank() == null) result.setRank(0); // Temporary placeholder
            examResultRepository.save(result);
        }

        // Trigger the Batch-Wide Ranking logic automatically
        rankBatchByExamResults(batchId);

        System.out.println("[RANKING AGENT] Finalized ranking for Batch: " + batchId);
        eventPublisher.publishEvent(new com.unios.domain.events.ExamResultsProcessedEvent(this, batchId));
    }

    @Transactional
    public void rankBatchByExamResults(Long batchId) {
        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new RuntimeException("Batch not found"));
        
        int capacity = batch.getSeatCapacity();
        int waitlistLimit = batch.getWaitlistCapacity();

        List<Application> applicants = applicationRepository.findByBatchId(batchId).stream()
                .filter(a -> examResultRepository.findByApplicationId(a.getId()).isPresent())
                .peek(a -> scoringService.calculateScore(a))
                .sorted(Comparator.comparing((Application a) -> 
                    a.getFinalScore() != null ? a.getFinalScore() : 0.0).reversed())
                .collect(Collectors.toList());

        for (int i = 0; i < applicants.size(); i++) {
            Application app = applicants.get(i);
            int rank = i + 1;
            
            // Also update the ExamResult object with the actual rank
            examResultRepository.findByApplicationId(app.getId()).ifPresent(res -> {
                res.setRank(rank);
                examResultRepository.save(res);
            });

            String subject = "UniOS: Your Entrance Exam Results";
            String body;
            if (i < capacity) {
                app.setStatus("COUNSELING_PENDING");
                body = "Congratulations " + app.getFullName() + "!\n\nYou have successfully cleared the entrance exam and are invited for the MANDATORY counseling session.\n\nPlease log in to the portal to view your schedule.";
            } else if (i < capacity + waitlistLimit) {
                app.setStatus("WAITLISTED");
                body = "Dear " + app.getFullName() + ",\n\nYou have been placed on the WAITLIST. We will notify you if a seat becomes available.";
            } else {
                app.setStatus("REJECTED");
                body = "Dear " + app.getFullName() + ",\n\nWe regret to inform you that you have been REJECTED for admission at this time.";
            }
            applicationRepository.save(app);
            emailService.sendEmail(app.getEmail(), subject, body);
        }
        
        System.out.println("[RANKING AGENT] Batch " + batchId + " ranking finalized: " + 
            Math.min(applicants.size(), capacity) + " PASSED, " + 
            Math.max(0, Math.min(applicants.size() - capacity, waitlistLimit)) + " WAITLISTED.");
    }
}
