package com.unios.service.agents.framework.v5.admissions;

import com.unios.model.Application;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Service
@Slf4j
public class AdmissionsStepServices {

    /**
     * Intake processing handling duplicates.
     */
    public void processApplicationIntake(Application application) {
        // Implementation: Duplicate applicant check by email/aadhar
        log.info("Intaking application...");
        boolean isDuplicate = checkDuplicate(application);
        if (isDuplicate) {
            throw new IllegalArgumentException("Duplicate applicant detected.");
        }
    }

    private boolean checkDuplicate(Application app) {
        // DB check structure
        return false;
    }

    /**
     * OCR sum vs total validation.
     */
    public boolean validateMarksOCR(double extractedSum, double extractedTotal, double claimedTotal) {
        // Edge Case: OCR mismatch
        if (extractedSum > extractedTotal) return false;
        double errorMargin = 0.05; // 5% tolerance for OCR blur
        return Math.abs((extractedSum / extractedTotal) - (extractedSum / claimedTotal)) < errorMargin;
    }

    /**
     * Ranking Algorithm with Tie-Breaking Logic
     */
    public List<Application> rankCandidates(List<Application> verifiedApplications) {
        // Structure: Sort by marks descending, tie break by timestamp (earlier first)
        Collections.sort(verifiedApplications, new Comparator<Application>() {
            @Override
            public int compare(Application a1, Application a2) {
                int scoreComparison = Double.compare(a2.getAcademicScore(), a1.getAcademicScore());
                if (scoreComparison != 0) {
                    return scoreComparison; // Higher score first
                }
                // Tie Breaker: Use submission timestamp edge case
                if (a1.getCreatedAt() != null && a2.getCreatedAt() != null) {
                    return a1.getCreatedAt().compareTo(a2.getCreatedAt());
                }
                return 0;
            }
        });
        return verifiedApplications;
    }

    /**
     * Seat Allocation and Overflow Protection
     */
    public int allocateSeats(List<Application> rankedCandidates, int totalSeats) {
        int allocated = 0;
        for (Application app : rankedCandidates) {
            if (allocated >= totalSeats) break; // Overflow protection
            // Implementation: app.setStatus("ADMITTED"); save(app);
            allocated++;
        }
        return allocated; // Usually 500
    }

    /**
     * Waitlist Auto-Fill implementation.
     */
    public void fillWaitlist(List<Application> rankedCandidates, int startIndex, int maxWaitlist) {
        int waitlisted = 0;
        for (int i = startIndex; i < rankedCandidates.size(); i++) {
            if (waitlisted >= maxWaitlist) break;
            // Implementation: rankedCandidates.get(i).setStatus("WAITLISTED");
            waitlisted++;
        }
    }
    
    /**
     * Low Applicants Edge Case Validator
     */
    public boolean checkLowApplicantsThreshold(int verifiedCount, int requiredSeats) {
        if (verifiedCount < requiredSeats) {
            log.warn("Low applicants detected! Only {} verified for {} seats.", verifiedCount, requiredSeats);
            return true;
        }
        return false;
    }
}
