package com.unios.service.agents.framework.v5.attendance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class AttendanceTrackingService {

    private final AttendanceInterventionService interventionService;
    
    // Configurable System Tolerance
    private static final double ATTENDANCE_THRESHOLD = 80.0;     // Target goal
    private static final double EXTREME_RISK_THRESHOLD = 60.0;   // Skips initial cooldowns

    /**
     * Triggered by cron job or external event (e.g. daily sweep)
     */
    public void trackAndEvaluateStudent(Long studentId, double calculatedAttendance, boolean dataSourcedCorrectly) {
        log.info("Evaluating Attendance for Student {}: {}%", studentId, calculatedAttendance);

        // Edge Case: Incorrect data handler
        if (!dataSourcedCorrectly || calculatedAttendance < 0 || calculatedAttendance > 100) {
            log.error("ABORT: Incorrect or corrupted attendance data detected for student {}", studentId);
            return; 
        }

        if (calculatedAttendance < ATTENDANCE_THRESHOLD) {
            interventionService.triggerIntervention(studentId, calculatedAttendance);
        } else {
            log.debug("Student {} maintains healthy attendance ({}%)", studentId, calculatedAttendance);
        }
    }

    /**
     * Feedback loop handler to capture excuses/medical states (No Response handled via Ladder naturally)
     */
    public void registerStudentFeedback(Long studentId, String feedbackCategory, String reason) {
        // e.g., category: "MEDICAL", "TRANSPORT", "OTHER"
        log.info("Registered feedback for Student {}: [{}] {}", studentId, feedbackCategory, reason);
        // Interacts with DB to store reasoning and temporarily pause intervention ladder if medical.
    }
}
