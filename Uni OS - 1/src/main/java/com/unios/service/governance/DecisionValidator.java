package com.unios.service.governance;

import com.unios.dto.DecisionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class DecisionValidator {

    private static final Map<String, List<String>> ALLOWED_ACTIONS = new HashMap<>();

    static {
        ALLOWED_ACTIONS.put("EligibilityAgent", List.of("UPDATE_STATUS", "REQUEST_MORE_INFO", "FINALIZE", "COMPLETE", "REJECT"));
        ALLOWED_ACTIONS.put("RiskMonitoringAgent", List.of("SEND_WARNING", "SCHEDULE_COUNSELING", "FINALIZE", "COMPLETE"));
        ALLOWED_ACTIONS.put("RecruitmentAgent", List.of("SHORTLIST", "REJECT_CANDIDATE", "SCHEDULE_INTERVIEW", "FINALIZE", "COMPLETE"));
        // Admissions goal agent — may audit vacancies and promote waitlisted candidates
        ALLOWED_ACTIONS.put("AdmissionsAgent", List.of("ADMISSIONS_AUDIT", "PROMOTE_WAITLIST", "FINALIZE", "COMPLETE", "CONTINUE"));
        // Attendance goal agent — may audit risk and send parent alerts
        ALLOWED_ACTIONS.put("AttendanceGuardian", List.of("ATTENDANCE_RISK_AUDIT", "SEND_PARENT_ALERT", "FINALIZE", "COMPLETE", "CONTINUE"));
    }

    /**
     * Validates if the agent's proposed action is within its allowed governance boundaries.
     */
    public boolean isValid(String agentName, DecisionResponse decision) {
        if (decision == null || decision.getAction() == null) return false;
        
        String action = decision.getAction().toUpperCase();
        
        // Finalization is always allowed
        if (action.contains("FINALIZE") || action.contains("COMPLETE")) return true;

        List<String> allowed = ALLOWED_ACTIONS.get(agentName);
        if (allowed == null) {
            log.warn("[GOVERNANCE] No specific guardrails defined for agent: {}. Allowing all.", agentName);
            return true;
        }

        boolean isAllowed = allowed.contains(action);
        if (!isAllowed) {
            log.error("[GOVERNANCE] VIOLATION: Agent {} attempted forbidden action: {}", agentName, action);
        }
        
        return isAllowed;
    }
}
