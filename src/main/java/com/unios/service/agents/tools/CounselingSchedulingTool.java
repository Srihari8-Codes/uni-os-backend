package com.unios.service.agents.tools;

import com.unios.model.Application;
import com.unios.model.CounselingSession;
import com.unios.repository.ApplicationRepository;
import com.unios.repository.CounselingSessionRepository;
import com.unios.service.agents.framework.AgentTool;
import com.unios.service.agents.framework.ToolContext;
import com.unios.service.agents.framework.ToolResult;
import com.unios.service.EmailService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;

@Component
public class CounselingSchedulingTool implements AgentTool {

    private final ApplicationRepository applicationRepository;
    private final CounselingSessionRepository counselingSessionRepository;
    private final EmailService emailService;

    public CounselingSchedulingTool(ApplicationRepository applicationRepository,
                                    CounselingSessionRepository counselingSessionRepository,
                                    EmailService emailService) {
        this.applicationRepository = applicationRepository;
        this.counselingSessionRepository = counselingSessionRepository;
        this.emailService = emailService;
    }

    @Override
    public String name() {
        return "CounselingSchedulingTool";
    }

    @Override
    public String description() {
        return "Schedules a counseling session and sends an invitation email. Input: {applicationId: Long, score: Double}";
    }

    @Override
    public ToolResult executeWithContext(ToolContext context) {
        Map<String, Object> input = context.getParameters();
        Long applicationId = Long.valueOf(input.get("applicationId").toString());
        double score = Double.parseDouble(input.getOrDefault("score", "0.0").toString());

        Application app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new RuntimeException("Application not found: " + applicationId));

        LocalDate counselingDate = LocalDate.now().plusDays(10);
        
        CounselingSession session = new CounselingSession();
        session.setApplication(app);
        session.setBatch(app.getBatch());
        session.setSessionDate(counselingDate);
        session.setTimeSlot("09:00 AM - 04:00 PM");
        session.setStatus("SCHEDULED");
        counselingSessionRepository.save(session);

        String counselingToken = String.format("CLG-%d-%04d", LocalDate.now().getYear(), app.getId());
        String subject = "UniOS Admissions: Counseling Invitation - " + counselingToken;
        String body = "Dear " + app.getFullName() + ",\n\n"
                + "Congratulations! You have been scheduled for counseling.\n"
                + "Reference Token: " + counselingToken + "\n"
                + "Date: " + counselingDate + "\n"
                + "Score: " + score;
        
        emailService.sendEmail(app.getEmail(), subject, body);

        return ToolResult.builder()
                .summary("Scheduled counseling for student " + app.getFullName())
                .status("SUCCESS")
                .actionData(Map.of("token", counselingToken, "date", counselingDate.toString()))
                .confidence(0.9)
                .build();
    }
}
