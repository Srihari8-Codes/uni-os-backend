package com.unios.service.event;

import com.unios.event.UniversityEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * UniversityEventPublisher — centralized gateway for publishing university events.
 *
 * Services use this to signal significant state changes (e.g. attendance drops).
 * Spring's ApplicationEventPublisher handles the delivery to all registered listeners.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UniversityEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public void publish(UniversityEvent event) {
        log.info("[EVENT PUBLISHED] ID: {} | Type: {} | Severity: {} | Source: {}",
                event.getId(), event.getType(), event.getSeverity(), event.getSource());
        applicationEventPublisher.publishEvent(event);
    }

    /**
     * Helper for quick attendance breach events
     */
    public void publishAttendanceBreach(Long studentId, double currentPct, String subject) {
        publish(UniversityEvent.of(
                UniversityEvent.EventType.ATTENDANCE_BREACH,
                currentPct < 65.0 ? UniversityEvent.Severity.CRITICAL : UniversityEvent.Severity.HIGH,
                "AttendanceService",
                java.util.Map.of("studentId", studentId, "attendancePct", currentPct, "subject", subject)
        ));
    }

    /**
     * Helper for single-day absence events (v4.1)
     */
    public void publishRandomAbsence(Long studentId, String subject) {
        publish(UniversityEvent.of(
                UniversityEvent.EventType.SINGLE_DAY_ABSENCE,
                UniversityEvent.Severity.MEDIUM,
                "AttendanceService",
                java.util.Map.of("studentId", studentId, "subject", subject)
        ));
    }

    /**
     * Helper for quick seat vacancy events
     */
    public void publishVacancyChange(Long batchId, int openSeats) {
        publish(UniversityEvent.of(
                UniversityEvent.EventType.SEAT_VACANCY_CHANGE,
                UniversityEvent.Severity.MEDIUM,
                "AdmissionsService",
                java.util.Map.of("batchId", batchId, "vacancyCount", openSeats)
        ));
    }
}
