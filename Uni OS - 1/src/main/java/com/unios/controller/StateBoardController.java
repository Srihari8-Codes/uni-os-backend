package com.unios.controller;

import com.unios.service.agents.framework.UniversityStateBoard;
import com.unios.service.agents.framework.UniversityStateSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * StateBoardController — exposes the UniversityStateBoard as a REST API.
 *
 * Allows administrators and dashboards to query the real-time state of
 * the university from a single endpoint. Also provides a manual refresh trigger
 * and a cross-agent conflict status check.
 */
@RestController
@RequestMapping("/api/state-board")
@RequiredArgsConstructor
public class StateBoardController {

    private final UniversityStateBoard stateBoard;

    /**
     * GET the full current university state snapshot.
     * This is the exact data object that agents see before making decisions.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UniversityStateSnapshot> getSnapshot() {
        return ResponseEntity.ok(stateBoard.getSnapshot());
    }

    /**
     * GET a lightweight map view of the current state (suitable for dashboards).
     */
    @GetMapping("/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getSummary() {
        return ResponseEntity.ok(stateBoard.getStateAsMap());
    }

    /**
     * POST manually trigger a state board refresh.
     */
    @PostMapping("/refresh")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> forceRefresh() {
        stateBoard.refreshState();
        return ResponseEntity.ok(Map.of("status", "StateBoard refreshed.",
                "health", stateBoard.getSnapshot().getSystemHealthStatus()));
    }

    /**
     * GET the cross-agent conflict status.
     * Returns true if Admissions and Attendance are working against each other.
     */
    @GetMapping("/conflict-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getConflictStatus() {
        UniversityStateSnapshot snap = stateBoard.getSnapshot();
        return ResponseEntity.ok(Map.of(
                "conflictDetected", snap.isAdmissionsAttendanceConflictDetected(),
                "attendanceTrend",  snap.getAttendanceTrend(),
                "vacancyRate",      snap.getOverallVacancyRate(),
                "atRiskRate",       snap.getAtRiskRate(),
                "recommendation",   snap.isAdmissionsAttendanceConflictDetected()
                        ? "Pause admissions promotions until attendance trend improves."
                        : "No conflict. Agents may operate normally."
        ));
    }
}
