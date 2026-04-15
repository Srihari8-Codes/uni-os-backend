package com.unios.service.agents.tools;

import com.unios.model.ParentNotification;
import com.unios.model.Student;
import com.unios.repository.ParentNotificationRepository;
import com.unios.repository.StudentRepository;
import com.unios.service.agents.framework.AgentTool;
import com.unios.service.agents.framework.ToolContext;
import com.unios.service.agents.framework.ToolResult;
import com.unios.service.llm.ResilientLLMService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * ParentAlertTool — INTELLIGENT EDITION with Escalation Logic
 *
 * Previously: Always sent the same style of message regardless of severity.
 * Now: Context-aware escalation with 3 tiers:
 *
 *   TIER 1 — MONITOR (risk 0.0–0.49):
 *     Gentle reminder. Tone: informative, supportive.
 *     Sent: once per week maximum.
 *
 *   TIER 2 — ALERT (risk 0.5–0.74):
 *     Firm alert. Tone: concerned, action-oriented.
 *     Sent: every 3 days.
 *
 *   TIER 3 — CRITICAL (risk 0.75–1.0):
 *     Urgent intervention request. Tone: serious, deadline-aware.
 *     Requests immediate parent-mentor meeting.
 *     Sent: daily.
 *
 * De-duplication: Will NOT send a repeat message if an alert of the same
 * tier was already sent within the cooldown window.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ParentAlertTool implements AgentTool {

    private final StudentRepository studentRepository;
    private final ParentNotificationRepository parentNotificationRepository;
    private final ResilientLLMService llmService;

    @Override
    public String name() {
        return "SEND_PARENT_ALERT";
    }

    @Override
    public String description() {
        return "Sends a context-aware, tiered parent alert. Escalates tone based on risk score. " +
               "Requires 'studentId', 'reason', and 'riskScore' in parameters.";
    }

    @Override
    public ToolResult executeWithContext(ToolContext context) {
        Long studentId = Long.valueOf(context.getParameters().get("studentId").toString());
        String reason = context.getParameters().getOrDefault("reason", "attendance concern").toString();
        double riskScore = context.getParameters().containsKey("riskScore")
                ? Double.parseDouble(context.getParameters().get("riskScore").toString())
                : 0.5;

        Student student = studentRepository.findById(studentId).orElse(null);
        if (student == null) {
            return ToolResult.builder()
                    .summary("ERROR: Student ID " + studentId + " not found.")
                    .reasoning("Student lookup failed. Cannot send alert.")
                    .confidence(1.0).status("FAILED").build();
        }

        if (student.getParentEmail() == null || student.getParentEmail().isBlank()) {
            return ToolResult.builder()
                    .summary("SKIPPED: No parent email on file for " + student.getFullName())
                    .reasoning("Student profile lacks parentEmail. Alert skipped. Recommend counselor to collect it.")
                    .confidence(1.0)
                    .status("PARTIAL")
                    .requiresFollowUp(true)
                    .suggestedNextAction("FINALIZE")
                    .build();
        }

        // ── TIER DETERMINATION ─────────────────────────────────────────────
        String tier = riskScore >= 0.75 ? "CRITICAL" : riskScore >= 0.5 ? "ALERT" : "MONITOR";
        int cooldownDays = tier.equals("CRITICAL") ? 1 : tier.equals("ALERT") ? 3 : 7;

        // ── DE-DUPLICATION CHECK ───────────────────────────────────────────
        LocalDateTime cutoff = LocalDateTime.now().minusDays(cooldownDays);
        List<ParentNotification> recentAlerts = parentNotificationRepository
                .findByStudentIdAndSentAtAfter(student.getId(), cutoff);

        if (!recentAlerts.isEmpty()) {
            return ToolResult.builder()
                    .summary(String.format("SKIPPED: %s already alerted within %d-day cooldown (tier: %s).",
                            student.getFullName(), cooldownDays, tier))
                    .reasoning("De-duplication check prevented repeat alert. Last sent: " +
                            recentAlerts.get(0).getSentAt())
                    .confidence(1.0)
                    .status("NO_ACTION_NEEDED")
                    .requiresFollowUp(false)
                    .build();
        }

        // ── ESCALATION PROMPT ENGINEERING ─────────────────────────────────
        String tone = switch (tier) {
            case "CRITICAL" -> "URGENT and very serious. Mention that the student risks being barred from exams. " +
                    "Request an immediate parent-mentor meeting within 48 hours.";
            case "ALERT"    -> "Firm and concerned. Clearly explain the attendance risk to exam eligibility. " +
                    "Request the parent to take supportive action this week.";
            default         -> "Gentle and supportive. Informally remind the parent about attendance " +
                    "and encourage the student.";
        };

        String systemPrompt = String.format(
                "You are a university communication agent. Generate a %s voice message script for the parent of %s. " +
                "Reason: %s. Risk level: %s (score: %.2f).",
                tone, student.getFullName(), reason, tier, riskScore);

        String userPrompt = String.format(
                "Generate a 2-3 sentence voice script in English. Address the parent directly. " +
                "Mention the student's name '%s' and the subject. Do not include salutation headers.",
                student.getFullName());

        String script = llmService.executeWithResilience(systemPrompt, userPrompt).getReasoning();

        // ── PERSIST NOTIFICATION ───────────────────────────────────────────
        ParentNotification notification = new ParentNotification();
        notification.setStudent(student);
        notification.setParentEmail(student.getParentEmail());
        notification.setAiMessage(script);
        notification.setType(tier);
        notification.setSentAt(LocalDateTime.now());
        parentNotificationRepository.save(notification);

        log.info("[PARENT ALERT] [{}] Sent to {} for {}. Risk={:.2f}",
                tier, student.getParentEmail(), student.getFullName(), riskScore);

        return ToolResult.builder()
                .summary(String.format("[%s] Alert sent to %s for student %s. Script: '%s'",
                        tier, student.getParentEmail(), student.getFullName(),
                        script != null ? script.substring(0, Math.min(80, script.length())) + "..." : "(generated)"))
                .reasoning(String.format("Risk score %.2f classified as %s tier. Cooldown: %d days. " +
                        "Used %s tone in LLM prompt.", riskScore, tier, cooldownDays, tier.toLowerCase()))
                .confidence(0.85)
                .actionData(Map.of("tier", tier, "email", student.getParentEmail(), "student", student.getFullName()))
                .requiresFollowUp(tier.equals("CRITICAL"))
                .suggestedNextAction(tier.equals("CRITICAL") ? "ATTENDANCE_RISK_AUDIT" : "FINALIZE")
                .status("SUCCESS")
                .build();
    }
}
