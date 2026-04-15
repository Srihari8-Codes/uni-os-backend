package com.unios.service.agents.hr;

import com.unios.model.Candidate;
import com.unios.repository.CandidateRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class RecruitmentAgent {

        private final CandidateRepository candidateRepository;
        private final ApplicationEventPublisher eventPublisher;
        private final com.unios.repository.FacultyRepository facultyRepository;
        private final com.unios.repository.InterviewScheduleRepository interviewScheduleRepository;
        private final com.unios.service.EmailService emailService;
        private final com.unios.service.llm.ReasoningEngineService reasoningEngineService;
        private final com.unios.service.agents.framework.AsyncAgentQueue asyncQueue;

        public RecruitmentAgent(CandidateRepository candidateRepository,
                        ApplicationEventPublisher eventPublisher,
                        com.unios.repository.FacultyRepository facultyRepository,
                        com.unios.repository.InterviewScheduleRepository interviewScheduleRepository,
                        com.unios.service.EmailService emailService,
                        com.unios.service.llm.ReasoningEngineService reasoningEngineService,
                        com.unios.service.agents.framework.AsyncAgentQueue asyncQueue) {
                this.candidateRepository = candidateRepository;
                this.eventPublisher = eventPublisher;
                this.facultyRepository = facultyRepository;
                this.interviewScheduleRepository = interviewScheduleRepository;
                this.emailService = emailService;
                this.reasoningEngineService = reasoningEngineService;
                this.asyncQueue = asyncQueue;
        }

        /**
         * Processes a newly applied candidate:
         * - Validates that required fields are present
         * - Shortlists the candidate (Using Reasoning Engine)
         * - Automatically schedules an interview
         * - Sends an email notification
         */
    @Transactional
    public void processCandidate(Long candidateId) {
        Candidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new RuntimeException("Candidate not found: " + candidateId));

        if (!"APPLIED".equals(candidate.getStatus())) {
            return;
        }

        String resumeText = candidate.getExtractedText() != null ? candidate.getExtractedText() : "N/A";
        
        String goal = String.format(
            "Finalize recruitment for %s in Department %s. " +
            "1. Analyze Resume: %s " +
            "2. If fit: Use CandidateStatusTool to set SHORTLISTED and perform manual scheduling steps. " +
            "3. If not fit: Set REJECTED.", 
            candidate.getFullName(), candidate.getDepartment(), resumeText
        );

        Map<String, String> context = new HashMap<>();
        context.put("candidateId", String.valueOf(candidateId));
        context.put("department", candidate.getDepartment());

        com.unios.service.agents.framework.AgentWorkTask task = com.unios.service.agents.framework.AgentWorkTask.builder()
            .taskId(UUID.randomUUID().toString())
            .agentName("RecruitmentAgent")
            .entityType("Candidate")
            .entityId(candidateId)
            .goal(goal)
            .context(context)
            .status("PENDING")
            .retryCount(0)
            .build();

        // Dispatch to background framework
        asyncQueue.enqueue(task);
    }
}
