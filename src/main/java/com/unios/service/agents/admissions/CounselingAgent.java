package com.unios.service.agents.admissions;

import com.unios.model.Application;
import com.unios.repository.ApplicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CounselingAgent {

    private final ApplicationRepository applicationRepository;
    private final com.unios.service.EmailService emailService;

    public CounselingAgent(ApplicationRepository applicationRepository,
            com.unios.service.EmailService emailService) {
        this.applicationRepository = applicationRepository;
        this.emailService = emailService;
    }

    @Transactional
    public void prepareForSingle(Long applicationId) {
        Application app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new RuntimeException("Application not found"));

        if (!"COUNSELING_PENDING".equals(app.getStatus())) return;

        // Send real email invitation
        String subject = "UniOS Admissions: Counseling Session Invitation";
        String body = "Dear " + app.getFullName() + ",\n\n"
                + "Congratulations! You have been shortlisted for the final counseling session. "
                + "Please login to your portal to choose your preferred time slot.\n\n"
                + "Best regards,\nUniversity Admissions Team";

        emailService.sendEmail(app.getEmail(), subject, body);
        System.out.println("[COUNSELING] Invitation sent to " + app.getEmail());
    }

    @Transactional
    public void prepareForCounseling(Long batchId) {
        List<Application> pendingApps = applicationRepository.findByBatchIdAndStatus(batchId, "COUNSELING_PENDING");
        // ... existing logic but could just call prepareForSingle in a loop if needed
        for (Application app : pendingApps) {
            prepareForSingle(app.getId());
        }
    }
}
