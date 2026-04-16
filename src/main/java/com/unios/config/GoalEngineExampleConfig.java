package com.unios.config;

import com.unios.model.Goal;
import com.unios.service.orchestrator.GoalEngineService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;

import java.time.ZonedDateTime;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class GoalEngineExampleConfig {

    private final GoalEngineService goalEngineService;

    @EventListener(ApplicationReadyEvent.class)
    @Order(2) // Run AFTER DatabaseInitializer (which is Order 1)
    public void deployExamples() {
        // Admissions goal (500 seats)
        goalEngineService.createGoal(
            Goal.GoalType.ADMISSIONS,
            "Fall 2026 Batch Enrollment",
            Map.of("maxWaitlistSize", 100, "genderRatioMin", 0.4),
            Map.of("seatsFilled", 500.0),
            ZonedDateTime.now().plusMonths(3)
        );

        // Attendance goal (80%)
        goalEngineService.createGoal(
            Goal.GoalType.ATTENDANCE,
            "Core Computing Sem 4 Attendance Stabilization",
            Map.of("maxAlertsPerDay", 50),
            Map.of("attendanceRate", 80.0),
            ZonedDateTime.now().plusDays(14)
        );
    }
}
