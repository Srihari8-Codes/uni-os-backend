package com.unios.service.event;

import com.unios.event.UniversityEvent;
import com.unios.model.ParentNotification;
import com.unios.repository.AttendanceRepository;
import com.unios.repository.ParentNotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * InterventionMonitorService — detects when AI-driven interventions fail.
 *
 * This service implements the "Failed intervention" requirement.
 * It checks for students who received a TIER 2 (ALERT) or TIER 3 (CRITICAL)
 * notification but whose attendance did not improve in the following 7 days.
 *
 * It publishes an INTERVENTION_FAIL event which triggers the senior
 * agentic response (e.g. human escalation).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InterventionMonitorService {

    private final ParentNotificationRepository parentNotificationRepository;
    private final AttendanceRepository attendanceRepository;
    private final UniversityEventPublisher eventPublisher;

    /**
     * Daily check for intervention effectiveness.
     * While technically a cron, its purpose is to trigger reactive events
     * for historical failures that cannot be caught at the moment of attendance marking.
     */
    @Scheduled(cron = "0 0 2 * * *") // Every night at 2 AM
    public void auditInterventionEffectiveness() {
        log.info("[INTERVENTION MONITOR] Auditing intervention effectiveness...");

        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        LocalDateTime fourteenDaysAgo = LocalDateTime.now().minusDays(14);

        // Find high-tier alerts sent between 7 and 14 days ago
        List<ParentNotification> oldAlerts = parentNotificationRepository.findAll().stream()
                .filter(n -> n.getSentAt() != null && 
                             n.getSentAt().isAfter(fourteenDaysAgo) && 
                             n.getSentAt().isBefore(sevenDaysAgo) &&
                             (n.getType().equals("ALERT") || n.getType().equals("CRITICAL")))
                .toList();

        for (ParentNotification alert : oldAlerts) {
            if (alert.getStudent() == null) continue;

            // Check attendance in the 7 days AFTER the alert
            long totalAfter = attendanceRepository.countBySlotEnrollmentIdAndDateAfter(
                    alert.getStudent().getId(), alert.getSentAt().toLocalDate());
            
            if (totalAfter < 3) continue; // Not enough data yet to judge failure

            long presentAfter = attendanceRepository.countBySlotEnrollmentIdAndDateAfterAndPresentTrue(
                    alert.getStudent().getId(), alert.getSentAt().toLocalDate());
            
            double postAlertPct = (double) presentAfter / totalAfter * 100.0;

            if (postAlertPct < 80.0) {
                log.warn("[INTERVENTION MONITOR] Intervention failed for Student ID {}. Attendance still at {:.1f}% post-alert.",
                        alert.getStudent().getId(), postAlertPct);

                eventPublisher.publish(UniversityEvent.of(
                        UniversityEvent.EventType.INTERVENTION_FAIL,
                        UniversityEvent.Severity.CRITICAL,
                        "InterventionMonitorService",
                        Map.of(
                                "studentId", alert.getStudent().getId(),
                                "studentName", alert.getStudent().getFullName(),
                                "alertId", alert.getId(),
                                "postAlertPct", postAlertPct,
                                "originalAlertTier", alert.getType()
                        )
                ));
            }
        }
    }
}
