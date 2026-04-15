package com.unios.service.policy;

import com.unios.model.SystemPolicy;
import com.unios.repository.SystemPolicyRepository;
import com.unios.repository.ExamResultRepository;
import com.unios.repository.AttendanceRepository;
import com.unios.repository.AgentDecisionLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class PolicyEngineService {

    private final SystemPolicyRepository policyRepository;
    private final ExamResultRepository examResultRepository;
    private final AttendanceRepository attendanceRepository;
    private final AgentDecisionLogRepository decisionLogRepository;

    public PolicyEngineService(SystemPolicyRepository policyRepository,
            ExamResultRepository examResultRepository,
            AttendanceRepository attendanceRepository,
            AgentDecisionLogRepository decisionLogRepository) {
        this.policyRepository = policyRepository;
        this.examResultRepository = examResultRepository;
        this.attendanceRepository = attendanceRepository;
        this.decisionLogRepository = decisionLogRepository;
    }

    /**
     * Get a policy value with a default fallback.
     * Records the default if the policy doesn't exist.
     */
    @Transactional
    public Double getPolicyValue(String key, Double defaultValue, String description) {
        Optional<SystemPolicy> policyOpt = policyRepository.findByPolicyKey(key);
        if (policyOpt.isPresent()) {
            return policyOpt.get().getPolicyValue();
        } else {
            // Initialize with default
            SystemPolicy newPolicy = new SystemPolicy(key, defaultValue, description, null);
            policyRepository.save(newPolicy);
            return defaultValue;
        }
    }

    /**
     * Feedback Loop: Analyze historical data to adjust policies.
     * Typically called at the end of a semester.
     */
    @Transactional
    public void analyzeSemester() {
        // 1. ELIGIBILITY_CUTOFF Adjustment
        // Logic: If pass rate > 90%, it's too easy -> Increase cutoff.
        // If pass rate < 50%, it's too hard -> Decrease cutoff.
        adjustEligibilityCutoff();

        // 2. RISK_THRESHOLD Adjustment
        // Logic: If too many students are flagged as "High Risk" but pass exams ->
        // Algorithm is too sensitive.
        // Increase threshold (make it harder to be high risk).
        adjustRiskThreshold();

        // 3. ROOM_UTILIZATION (e.g. max capacity usage)
        // Logic: If rooms are consistently full, maybe we allow slightly higher packing
        // or request more rooms.
        // This is a placeholder for now.
    }

    private void adjustEligibilityCutoff() {
        // Simplified Logic: Check average scores or pass rates.
        // Since we might not have 'ExamResult' populated with comprehensive data yet,
        // we use a heuristic.

        long totalResults = examResultRepository.count();
        if (totalResults == 0)
            return;

        // Count students with marks > 40 (Pass)
        // Assuming ExamResult has 'marksObtained'
        // We need to check ExamResult entity.
        // For now, let's assume we can fetch all and calculate.

        // This is expensive for large data, but fine for prototype.
        double avgMarks = examResultRepository.findAll().stream()
                .mapToDouble(r -> r.getScore() != null ? r.getScore() : 0)
                .average()
                .orElse(0.0);

        Double currentCutoff = getPolicyValue("ELIGIBILITY_CUTOFF", 60.0, "Minimum score for admission eligibility");

        if (avgMarks > 85.0) {
            // Exam was too easy or students are too good. Make eligibility harder.
            currentCutoff += 2.0;
        } else if (avgMarks < 50.0) {
            // Exam was too hard. Lower eligibility.
            currentCutoff -= 2.0;
        }

        // Clamp
        if (currentCutoff < 40.0)
            currentCutoff = 40.0;
        if (currentCutoff > 90.0)
            currentCutoff = 90.0;

        updatePolicy("ELIGIBILITY_CUTOFF", currentCutoff);
    }

    private void adjustRiskThreshold() {
        // Logic: Check how many students flagged as 'At Risk' actually failed.
        // For now, simple heuristic:
        // If 'Attendance' average is high (>90%), increase RISK_THRESHOLD (make it
        // harder to flag).

        long count = attendanceRepository.count();
        if (count == 0)
            return;

        // Mock logic using AgentDecisionLog to see how many warnings were issued
        long riskWarnings = decisionLogRepository.findAll().stream()
                .filter(log -> log.getDecision() != null && log.getDecision().contains("HIGH RISK"))
                .count();

        Double currentThreshold = getPolicyValue("RISK_ATTENDANCE_THRESHOLD", 75.0,
                "Attendance % below which student is At Risk");

        if (riskWarnings > (count * 0.5)) {
            // More than 50% students warned! Too sensitive.
            // Lower the threshold (e.g. from 75% to 70%) so fewer people are flagged.
            currentThreshold -= 5.0;
        } else if (riskWarnings == 0) {
            // No one warned? Maybe too lenient.
            currentThreshold += 2.0;
        }

        updatePolicy("RISK_ATTENDANCE_THRESHOLD", currentThreshold);
    }

    @org.springframework.context.event.EventListener
    @Transactional
    public void onRiskDetected(com.unios.domain.events.RiskDetectedEvent event) {
        System.out.println(
                "[POLICY] Risk Detected for Student " + event.getStudentId() + ". Evaluating Policy Impact...");

        // Example Logic: If a risk is detected, we might want to temporarily TIGHTEN or
        // RELAX rules.
        // Prompt Example: "If >20% students high risk: Reduce maxFailed threshold by
        // 1."
        // Implementing simple logic: Increment a counter or check globally?
        // For stateless simplicity, let's just do a localized adjustment check.

        // Let's assume we want to be stricter if risks are popping up.
        // Or actually, if many people fail (Risk), maybe the threshold
        // 'MAX_FAILED_COURSES' is too loose?
        // Prompt says: "Reduce maxFailed threshold" (Make it stricter? Or easier?
        // Reducing max failing means you get flagged SOONER. So stricter).

        // Let's check total risk counts.
        long riskCount = decisionLogRepository.count(); // Using decision log as proxy or add specific repo query.
        // Actually, let's use Attendance as a proxy for total students.
        long totalStudents = attendanceRepository.count(); // Rough proxy.

        if (totalStudents > 0) {
            // In a real system we'd query active risks.
            // Here we just react to the event.

            Double currentMaxFailed = getPolicyValue("MAX_FAILED_COURSES", 2.0,
                    "Max failed courses before High Risk auto-flag");
            if (currentMaxFailed > 1.0) {
                // updatePolicy("MAX_FAILED_COURSES", currentMaxFailed - 0.1); // Gradual
                // tightening
                // System.out.println("[POLICY] Auto-Approving tighter risk controls.");
            }
        }
    }

    private void updatePolicy(String key, Double value) {
        SystemPolicy policy = policyRepository.findByPolicyKey(key)
                .orElse(new SystemPolicy(key, value, "", null));
        policy.setPolicyValue(value);
        policyRepository.save(policy);
        System.out.println("POLICY UPDATE: " + key + " -> " + value);
    }
}
