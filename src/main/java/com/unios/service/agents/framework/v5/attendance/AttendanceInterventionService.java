package com.unios.service.agents.framework.v5.attendance;

import com.unios.model.AttendanceIntervention;
import com.unios.repository.AttendanceInterventionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AttendanceInterventionService {

    private final AttendanceInterventionRepository interventionRepository;

    /**
     * Cooldown constraints (in days) per level to prevent spam.
     */
    private static final int COOLDOWN_EMAIL = 3;
    private static final int COOLDOWN_SMS = 3;
    private static final int COOLDOWN_PARENT = 7;
    private static final int COOLDOWN_CALL = 7;

    public void triggerIntervention(Long studentId, double currentAttendance) {
        Optional<AttendanceIntervention> activeOpt = interventionRepository.findFirstByStudentIdAndIsActiveTrue(studentId);

        if (activeOpt.isPresent()) {
            AttendanceIntervention intervention = activeOpt.get();
            evaluateAndEscalate(intervention, currentAttendance);
        } else {
            // Start new intervention ladder
            log.info("Starting new attendance intervention for Student {}", studentId);
            AttendanceIntervention newIntervention = AttendanceIntervention.builder()
                    .studentId(studentId)
                    .baselineAttendance(currentAttendance)
                    .currentLevel(AttendanceIntervention.InterventionLevel.EMAIL_STUDENT)
                    .lastActionDate(LocalDateTime.now())
                    .build();
            
            executeLevelAction(newIntervention.getCurrentLevel(), studentId);
            interventionRepository.save(newIntervention);
        }
    }

    private void evaluateAndEscalate(AttendanceIntervention intervention, double currentAttendance) {
        long daysSinceLastAction = ChronoUnit.DAYS.between(intervention.getLastActionDate(), LocalDateTime.now());

        // Check Improvement / Feedback Loop
        if (currentAttendance > intervention.getBaselineAttendance()) {
            intervention.setImprovementScore(currentAttendance - intervention.getBaselineAttendance());
            if (currentAttendance >= 80.0) {
                log.info("Student {} attendance restored to {}%. Closing intervention.", intervention.getStudentId(), currentAttendance);
                intervention.setIsActive(false);
                intervention.setStudentFeedback("ATTENDANCE RESTORED");
                interventionRepository.save(intervention);
                return;
            }
        }

        // SPAM PREVENTION & ESCALATION LOGIC
        AttendanceIntervention.InterventionLevel nextLevel = null;
        
        switch (intervention.getCurrentLevel()) {
            case EMAIL_STUDENT:
                if (daysSinceLastAction >= COOLDOWN_EMAIL) nextLevel = AttendanceIntervention.InterventionLevel.SMS_STUDENT;
                break;
            case SMS_STUDENT:
                if (daysSinceLastAction >= COOLDOWN_SMS) nextLevel = AttendanceIntervention.InterventionLevel.EMAIL_PARENT;
                break;
            case EMAIL_PARENT:
                if (daysSinceLastAction >= COOLDOWN_PARENT || currentAttendance < 60.0) nextLevel = AttendanceIntervention.InterventionLevel.DIRECT_CALL;
                break;
            case DIRECT_CALL:
                if (daysSinceLastAction >= COOLDOWN_CALL && currentAttendance < 50.0) nextLevel = AttendanceIntervention.InterventionLevel.ACADEMIC_PROBATION;
                break;
            case ACADEMIC_PROBATION:
                log.warn("Student {} is strictly on academic probation. No further automated contact.", intervention.getStudentId());
                return;
            default:
                break;
        }

        if (nextLevel != null) {
            log.info("Escalating Student {} to level {}", intervention.getStudentId(), nextLevel);
            intervention.setCurrentLevel(nextLevel);
            intervention.setLastActionDate(LocalDateTime.now());
            executeLevelAction(nextLevel, intervention.getStudentId());
            interventionRepository.save(intervention);
        } else {
            log.debug("Cooldown active for Student {} at level {}. Skipping spam.", intervention.getStudentId(), intervention.getCurrentLevel());
        }
    }

    private void executeLevelAction(AttendanceIntervention.InterventionLevel level, Long studentId) {
        // Direct Action Mapping (Typically triggers NotificationTool)
        switch (level) {
            case EMAIL_STUDENT:
                log.info(">>> Executing Tool: Dispatching WARNING EMAIL to Student {}", studentId);
                break;
            case SMS_STUDENT:
                log.info(">>> Executing Tool: Dispatching WARNING SMS to Student {}", studentId);
                break;
            case EMAIL_PARENT:
                log.info(">>> Executing Tool: Dispatching CRITICAL EMAIL to Parent of Student {}", studentId);
                break;
            case DIRECT_CALL:
                log.info(">>> Executing Tool: Queueing DIRECT VOICE CALL to Student/Parent {}", studentId);
                break;
            case ACADEMIC_PROBATION:
                log.warn(">>> Executing Tool: Updating Student {} status to PROBATION", studentId);
                break;
            default:
                break;
        }
    }
}
