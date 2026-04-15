package com.unios.service.agents.framework;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * UniversityStateSnapshot — Immutable point-in-time view of university health.
 *
 * This is the payload that the UniversityStateBoard makes available to all agents.
 * It replaces isolated per-tool database queries with a single, pre-computed view
 * of the entire university, enabling cross-domain decision-making.
 *
 * Fields are grouped by domain so agents can speak across cycles:
 *   - ADMISSIONS metrics (batch fill rates, pressure indicators)
 *   - ATTENDANCE metrics (overall health, at-risk counts, trend direction)
 *   - INTERVENTION metrics (alert effectiveness, action history)
 *   - SYSTEM metrics (agent cycle health, conflict flags)
 */
@Data
@Builder
public class UniversityStateSnapshot {

    // ── METADATA ────────────────────────────────────────────────────────────
    private LocalDateTime capturedAt;

    // ── ADMISSIONS DOMAIN ────────────────────────────────────────────────────

    /** Total active batch capacity across the university */
    private int totalBatchCapacity;

    /** Total enrolled students across all active batches */
    private int totalEnrolled;

    /** Overall seat vacancy rate: 0.0 (full) → 1.0 (empty) */
    private double overallVacancyRate;

    /** Number of active batches with at least one vacant seat */
    private int batchesWithVacancies;

    /** Total waitlisted applicants across all batches */
    private int totalWaitlisted;

    /**
     * Admission pressure index: ratio of waitlisted to vacancies.
     * > 1.0 = demand exceeds supply (high quality pool available)
     * < 1.0 = more seats than qualified applicants (harder to fill)
     */
    private double admissionPressureIndex;

    // ── ATTENDANCE DOMAIN ────────────────────────────────────────────────────

    /** Average attendance percentage across all active subject offerings */
    private double averageAttendancePct;

    /** Number of students currently below 80% eligibility threshold */
    private int atRiskStudentCount;

    /** Number of students in CRITICAL risk tier (risk score ≥ 0.75) */
    private int criticalRiskStudentCount;

    /** Attendance trend direction: IMPROVING, STABLE, DECLINING */
    private String attendanceTrend;

    /** Proportion of all enrolled students who are at risk */
    private double atRiskRate;

    // ── INTERVENTION DOMAIN ───────────────────────────────────────────────────

    /** Parent alerts sent in the last 24 hours */
    private int alertsSentLast24h;

    /** Estimated alert effectiveness rate (alerts with follow-up improvement) */
    private double alertEffectivenessRate;

    // ── SYSTEM / CROSS-AGENT FLAGS ────────────────────────────────────────────

    /**
     * True if Admissions is actively filling batches that Attendance
     * shows are systemically struggling. Cross-agent conflict detected.
     */
    private boolean admissionsAttendanceConflictDetected;

    /**
     * The latest agent action taken by each agent, for coordination.
     * key = agentName, value = last action + timestamp
     */
    private Map<String, String> lastAgentActions;

    /**
     * Overall system health: HEALTHY | STRESSED | CRITICAL
     * Composite of vacancy rate, at-risk rate, and trend.
     */
    private String systemHealthStatus;
}
