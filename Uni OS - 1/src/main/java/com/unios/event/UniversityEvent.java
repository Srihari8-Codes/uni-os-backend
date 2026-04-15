package com.unios.event;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * UniversityEvent — The core data structure for reactive agent activation.
 *
 * Replaces the "wait for 15 minutes" cron pattern with "react immediately".
 * Events carry severity and metadata to help the orchestrator prioritize actions.
 */
@Getter
@Builder
public class UniversityEvent {
    public enum EventType {
        ATTENDANCE_BREACH,   // Student dipped below threshold
        SINGLE_DAY_ABSENCE,  // Student marked absent for a specific class
        SEAT_VACANCY_CHANGE, // Batch seat became available
        INTERVENTION_FAIL,  // Parent alert didn't improve attendance
        SYSTEM_CONFLICT      // Cross-agent conflict detected by StateBoard
    }

    public enum Severity {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    private final String id;
    private final EventType type;
    private final Severity severity;
    private final String source;
    private final Map<String, Object> metadata;
    private final LocalDateTime timestamp;

    public static UniversityEvent of(EventType type, Severity severity, String source, Map<String, Object> metadata) {
        return UniversityEvent.builder()
                .id(java.util.UUID.randomUUID().toString())
                .type(type)
                .severity(severity)
                .source(source)
                .metadata(metadata)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
