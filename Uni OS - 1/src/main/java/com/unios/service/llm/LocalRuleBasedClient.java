package com.unios.service.llm;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import com.unios.service.orchestrator.InstitutionalOrchestrator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LocalRuleBasedClient implements LLMClient {

    @Override
    public String generateResponse(String systemPrompt, String userPrompt) {
        String lowerSystem = systemPrompt.toLowerCase();
        logToOrch("Matching context for: " + (systemPrompt.length() > 50 ? systemPrompt.substring(0, 50) : systemPrompt));

        if (lowerSystem.contains("ranking")) {
            return handleRanking(userPrompt);
        } else if (lowerSystem.contains("admissions officer") || lowerSystem.contains("eligibility")) {
            return handleEligibility(userPrompt);
        } else if (lowerSystem.contains("hr manager")) {
            return handleRecruitment(userPrompt);
        } else if (lowerSystem.contains("academic advisor")) {
            return handleRiskAssessment(userPrompt);
        } else if (lowerSystem.contains("registrar")) {
            return handleCertification(userPrompt);
        } else if (lowerSystem.contains("documentanalyzer") || lowerSystem.contains("analysis")) {
            return handleDocumentAnalysis(userPrompt);
        } else if (lowerSystem.contains("absence") || lowerSystem.contains("transcript")) {
            return handleAbsenceReason(userPrompt);
        }
        return "{\"action\": \"ERROR\", \"reasoning\": \"Unrecognized prompt context.\"}";
    }

    private String handleAbsenceReason(String prompt) {
        if (prompt == null || prompt.trim().isEmpty() || prompt.toLowerCase().contains("transcript: null") || prompt.toLowerCase().contains("transcript: \"\"")) {
            return "{\"action\": \"CALL_FAILED\", \"reasoning\": \"No transcript available. Call may have failed or was too short.\", \"parameters\": {}, \"confidence\": 0.0}";
        }

        String lower = prompt.toLowerCase();
        String reason = "Generic reason or follow-up required";
        
        if (lower.contains("fever") || lower.contains("sick") || lower.contains("hospital")) {
            reason = "Medical/Health issues (Fever/Sickness)";
        } else if (lower.contains("travel") || lower.contains("marriage") || lower.contains("out of town")) {
            reason = "Family/Travel commitments";
        } else if (lower.contains("accident") || lower.contains("emergency")) {
            reason = "Emergency situation";
        } else if (lower.contains("not feeling well") || lower.contains("headache")) {
            reason = "Personal health concerns";
        }
        
        return "{\"action\": \"REASON_EXTRACTED\", \"reasoning\": \"" + reason + "\", \"parameters\": {}, \"confidence\": 0.8}";
    }

    private String handleRanking(String prompt) {
        // Expected: "Student: ... Entrance Exam Score: 75.0"
        double score = extractDouble(prompt, "Entrance Exam Score");
        if (score >= 60) {
            return "{\"action\": \"EXAM_PASSED\", \"reasoning\": \"Qualified candidate with score " + score + ". Proceed to counseling.\"}";
        } else if (score >= 50) {
            return "{\"action\": \"WAITLISTED\", \"reasoning\": \"Marginal performance with score " + score + ". Move to waitlist.\"}";
        }
        return "{\"action\": \"EXAM_FAILED\", \"reasoning\": \"Score " + score + " below minimum threshold.\"}";
    }

    private String handleCertification(String prompt) {
        // Expected: "Student: ... Department: Mechanical Engineering"
        // Logic: Return "Bachelor of Technology in [Department]"

        String department = "Engineering";
        if (prompt.contains("Department:")) {
            department = prompt.substring(prompt.indexOf("Department:") + 11).trim();
            if (department.contains(".")) {
                department = department.substring(0, department.indexOf(".")).trim();
            }
        }

        return "{\"action\": \"CERTIFIED\", \"reasoning\": \"Bachelor of Technology in " + department + " (Verified all academic and credit requirements)\"}";
    }

    private String handleEligibility(String prompt) {
        // Expected Prompt: "Applicant: John Doe. Document Text: ... Physics: 90,
        // Chemistry: 85 ..."
        // Logic: Extract scores, check avg > 60.

        // If we are reflecting on a tool success, finalize.
        if (prompt.contains("Updated application")) {
            return "{\"action\": \"FINALIZE\", \"reasoning\": \"Application status updated successfully.\", \"confidence\": 1.0}";
        }

        int physics = extractScore(prompt, "Physics");
        int chemistry = extractScore(prompt, "Chemistry");
        int math = extractScore(prompt, "Math");

        // If scores missing, try to find "Score: XX" or similar generic pattern
        if (physics == 0 && chemistry == 0 && math == 0) {
            // Fallback for "Academic Score: 85"
            int academicScore = extractScore(prompt, "Academic Score");
            if (academicScore > 60)
                return "{\"action\": \"ApplicationStatusTool\", \"parameters\": {\"status\": \"ELIGIBLE\"}, \"reasoning\": \"Based on Academic Score\", \"confidence\": 1.0}";
            return "{\"action\": \"ApplicationStatusTool\", \"parameters\": {\"status\": \"INELIGIBLE\"}, \"reasoning\": \"Insufficient Academic Score\", \"confidence\": 1.0}";
        }

        double avg = (physics + chemistry + math) / 3.0;
        if (avg > 60) {
            return "{\"action\": \"ApplicationStatusTool\", \"parameters\": {\"status\": \"ELIGIBLE\"}, \"reasoning\": \"Average Score: " + String.format("%.2f", avg) + "\", \"confidence\": 1.0}";
        }
        return "{\"action\": \"ApplicationStatusTool\", \"parameters\": {\"status\": \"INELIGIBLE\"}, \"reasoning\": \"Average Score: " + String.format("%.2f", avg) + " < 60\", \"confidence\": 1.0}";
    }

    private String handleRecruitment(String prompt) {
        // Expected: "Candidate Name: ... Department: Computer Science"
        // Logic: Accept CS, Engineering, IT. Reject others.

        String lower = prompt.toLowerCase();
        if (lower.contains("computer science") || lower.contains("engineering")
                || lower.contains("information technology")) {
            return "{\"action\": \"SELECTED\", \"reasoning\": \"Department match\", \"confidence\": 1.0}";
        }
        return "{\"action\": \"REJECTED\", \"reasoning\": \"Department not priority\", \"confidence\": 1.0}";
    }

    private String handleDocumentAnalysis(String prompt) {
        // Mocked OCR Extract: 431/600 (71.83%) - Nested in 'parameters' to match DecisionResponse DTO
        return "{\"action\": \"ANALYSIS_COMPLETE\", \"parameters\": {\"obtainedMarks\": 431, \"totalMarks\": 600, \"percentage\": 71.83}, \"reasoning\": \"(FALLBACK) Found 431/600 marks for student\", \"confidence\": 1.0}";
    }

    private String handleRiskAssessment(String prompt) {
        // Expected: "Earned Credits: 10. Failed Courses: 2"
        // Logic: Failed > 0 -> High Risk.

        int failed = extractScore(prompt, "Failed Courses");
        int earned = extractScore(prompt, "Earned Credits");

        if (failed > 0) {
            return "{\"decision\": \"High Risk\", \"reasoning\": \"Failed " + failed + " courses\"}";
        }
        if (earned < 15) {
            return "{\"decision\": \"Moderate Risk\", \"reasoning\": \"Low credits\"}";
        }
        return "{\"decision\": \"Active\", \"reasoning\": \"Good standing\"}";
    }

    private int extractScore(String text, String label) {
        return (int) extractDouble(text, label);
    }

    private double extractDouble(String text, String label) {
        // Regex to find "Label: 90.5" or "Label 90.5"
        Pattern p = Pattern.compile(label + "[:\\s]+(\\d+\\.?\\d*)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(text);
        if (m.find()) {
            return Double.parseDouble(m.group(1));
        }
        return 0.0; // Not found
    }
    private void logToOrch(String msg) {
        InstitutionalOrchestrator.ACTIVITY_LOG.add("[FALLBACK] " + java.time.LocalDateTime.now() + ": " + msg);
    }

    @Override
    public String generateResponseWithImage(String systemPrompt, String userPrompt, String base64Image) {
        return null;
    }
}
