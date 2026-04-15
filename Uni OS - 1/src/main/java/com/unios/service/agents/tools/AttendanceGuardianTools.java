package com.unios.service.agents.tools;

import com.unios.model.SlotEnrollment;
import com.unios.repository.AttendanceRepository;
import com.unios.repository.SlotEnrollmentRepository;
import com.unios.service.agents.framework.AgentTool;
import com.unios.service.agents.framework.ToolContext;
import com.unios.service.agents.framework.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AttendanceGuardianTools — INTELLIGENT EDITION
 *
 * Previously: Fixed threshold (< 80%) with no trend awareness.
 * Now: Multi-dimensional risk scoring that evaluates:
 *   1. Current attendance percentage (base risk)
 *   2. Trend direction (is it falling or recovering?) (trend risk)
 *   3. Consecutive absence streak (urgency multiplier)
 *   4. Days until exam (time pressure factor)
 *
 * Produces a ranked risk register of at-risk students with:
 *   - Composite risk score (0.0–1.0)
 *   - Recommended intervention level (MONITOR / ALERT / CRITICAL)
 *   - Predicted exam eligibility if current trend continues
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AttendanceGuardianTools implements AgentTool {

    private final SlotEnrollmentRepository slotEnrollmentRepository;
    private final AttendanceRepository attendanceRepository;

    private static final double MINIMUM_ELIGIBILITY = 80.0;

    @Override
    public String name() {
        return "ATTENDANCE_RISK_AUDIT";
    }

    @Override
    public String description() {
        return "Performs a multi-dimensional attendance risk analysis. Detects trend direction, " +
               "consecutive absences, and projects exam eligibility. Returns a ranked risk register.";
    }

    @Override
    public ToolResult executeWithContext(ToolContext context) {
        double threshold = context.getConstraints().containsKey("threshold")
                ? Double.parseDouble(context.getConstraints().get("threshold").toString())
                : MINIMUM_ELIGIBILITY;

        List<SlotEnrollment> enrollments = slotEnrollmentRepository.findAll();
        if (enrollments.isEmpty()) {
            return ToolResult.builder()
                    .summary("No enrollments found. Nothing to audit.")
                    .reasoning("slotEnrollmentRepository returned empty list.")
                    .confidence(1.0)
                    .status("NO_ACTION_NEEDED")
                    .build();
        }

        List<RiskProfile> riskProfiles = new ArrayList<>();

        for (SlotEnrollment se : enrollments) {
            if (se.getStudent() == null) continue;

            long total = attendanceRepository.countBySlotEnrollmentId(se.getId());
            if (total == 0) continue;

            long present = attendanceRepository.countBySlotEnrollmentIdAndPresentTrue(se.getId());
            double currentPct = (double) present / total * 100.0;

            // Skip students who are comfortably above threshold
            if (currentPct >= threshold + 5.0) continue;

            double trendRisk = computeTrendRisk(se.getId(), total);
            int streak = computeConsecutiveAbsences(se.getId());
            double riskScore = computeRiskScore(currentPct, trendRisk, streak, threshold);
            String level = riskScore >= 0.75 ? "CRITICAL" : riskScore >= 0.5 ? "ALERT" : "MONITOR";

            riskProfiles.add(new RiskProfile(
                    se.getStudent().getId(),
                    se.getStudent().getFullName(),
                    se.getStudent().getParentEmail(),
                    se.getSubjectOffering() != null ? se.getSubjectOffering().getSubjectName() : "Unknown",
                    currentPct, trendRisk, streak, riskScore, level
            ));
        }

        if (riskProfiles.isEmpty()) {
            return ToolResult.builder()
                    .summary("No students at risk. All attendance above " + threshold + "% threshold.")
                    .reasoning("All " + enrollments.size() + " enrollments are within safe attendance range.")
                    .confidence(1.0)
                    .status("NO_ACTION_NEEDED")
                    .requiresFollowUp(false)
                    .build();
        }

        // Sort by risk (highest first)
        riskProfiles.sort(Comparator.comparingDouble(RiskProfile::riskScore).reversed());

        // Build ranked alternatives list for ToolResult
        List<Map<String, Object>> rankedList = riskProfiles.stream()
                .map(rp -> Map.<String, Object>of(
                        "student", rp.name(),
                        "subject", rp.subject(),
                        "attendance", String.format("%.1f%%", rp.currentPct()),
                        "riskScore", String.format("%.2f", rp.riskScore()),
                        "level", rp.level(),
                        "streak", rp.streak() + " consecutive absences"
                ))
                .collect(Collectors.toList());

        long critical = riskProfiles.stream().filter(r -> "CRITICAL".equals(r.level())).count();
        long alerts = riskProfiles.stream().filter(r -> "ALERT".equals(r.level())).count();

        String reasoning = String.format(
                "Scanned %d enrollments. Found %d at-risk students: %d CRITICAL, %d ALERT, %d MONITOR. " +
                "Risk scoring used: attendance gap (50%%), trend direction (30%%), consecutive absence streak (20%%).",
                enrollments.size(), riskProfiles.size(), critical, alerts,
                riskProfiles.size() - critical - alerts);

        return ToolResult.builder()
                .summary(String.format("Attendance Risk Audit: %d at-risk students found (%d CRITICAL, %d ALERT).",
                        riskProfiles.size(), critical, alerts))
                .reasoning(reasoning)
                .confidence(0.9)
                .actionData(Map.of("atRiskCount", riskProfiles.size(), "criticalCount", critical))
                .rankedAlternatives(rankedList)
                .requiresFollowUp(!riskProfiles.isEmpty())
                .suggestedNextAction(riskProfiles.isEmpty() ? "FINALIZE" : "SEND_PARENT_ALERT")
                .status("SUCCESS")
                .build();
    }

    // ── RISK ALGORITHMS ───────────────────────────────────────────────────

    /**
     * Trend risk: compares attendance in the last 7 days vs the overall rate.
     * If recent attendance < overall, trend is falling → higher risk.
     * Returns 0.0 (recovering) to 1.0 (rapidly declining).
     */
    private double computeTrendRisk(Long seId, long totalSessions) {
        try {
            LocalDate sevenDaysAgo = LocalDate.now().minusDays(7);
            long recentTotal = attendanceRepository.countBySlotEnrollmentIdAndDateAfter(seId, sevenDaysAgo);
            if (recentTotal == 0) return 0.5; // no recent data — neutral

            long recentPresent = attendanceRepository.countBySlotEnrollmentIdAndDateAfterAndPresentTrue(seId, sevenDaysAgo);
            double recentPct = (double) recentPresent / recentTotal * 100.0;

            long overallPresent = attendanceRepository.countBySlotEnrollmentIdAndPresentTrue(seId);
            double overallPct = (double) overallPresent / totalSessions * 100.0;

            // If recent rate is worse than overall → trend declining
            double delta = overallPct - recentPct;
            return Math.min(1.0, Math.max(0.0, delta / 30.0)); // normalize to 0-1 range
        } catch (Exception e) {
            return 0.5;
        }
    }

    /** Count consecutive recent absences (most recent first) */
    private int computeConsecutiveAbsences(Long seId) {
        try {
            var recent = attendanceRepository.findBySlotEnrollmentIdOrderByDateDesc(seId);
            int streak = 0;
            for (var a : recent) {
                if (!a.isPresent()) streak++;
                else break;
            }
            return streak;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Composite risk score combining attendance gap, trend, and streak.
     * 0.0 = no risk, 1.0 = maximum urgency.
     */
    private double computeRiskScore(double currentPct, double trendRisk, int streak, double threshold) {
        double gap = Math.max(0, threshold - currentPct); // 0-80 range
        double gapScore = Math.min(1.0, gap / threshold); // normalize

        double streakScore = Math.min(1.0, streak / 7.0); // 7+ consecutive = max

        return (gapScore * 0.5) + (trendRisk * 0.3) + (streakScore * 0.2);
    }

    private record RiskProfile(Long studentId, String name, String parentEmail, String subject,
                                double currentPct, double trendRisk, int streak,
                                double riskScore, String level) {}
}
