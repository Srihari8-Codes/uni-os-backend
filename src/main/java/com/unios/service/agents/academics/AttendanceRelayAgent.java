package com.unios.service.agents.academics;

import com.unios.model.*;
import com.unios.repository.*;
import com.unios.service.llm.ResilientLLMService;
import com.unios.dto.DecisionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class AttendanceRelayAgent {

    private final AttendanceRepository attendanceRepository;
    private final SlotEnrollmentRepository slotEnrollmentRepository;
    private final ParentNotificationRepository parentNotificationRepository;
    private final ResilientLLMService llmService;

    public AttendanceRelayAgent(AttendanceRepository attendanceRepository,
                                SlotEnrollmentRepository slotEnrollmentRepository,
                                ParentNotificationRepository parentNotificationRepository,
                                ResilientLLMService llmService) {
        this.attendanceRepository = attendanceRepository;
        this.slotEnrollmentRepository = slotEnrollmentRepository;
        this.parentNotificationRepository = parentNotificationRepository;
        this.llmService = llmService;
    }

    /**
     * Daily task to check for absences and notify parents.
     */
    @Scheduled(cron = "0 0 18 * * *") // Runs at 6 PM daily
    @Transactional
    public void runDailyAttendanceAudit() {
        log.info("[ATTENDANCE AGENT] Starting daily attendance audit...");
        LocalDate today = LocalDate.now();
        
        // 1. Check for today's absences
        List<Attendance> todaysAbsences = attendanceRepository.findByDateAndPresentFalse(today);
        for (Attendance attendance : todaysAbsences) {
            SlotEnrollment se = attendance.getSlotEnrollment();
            if (se != null && se.getStudent() != null && se.getStudent().getParentEmail() != null) {
                notifyParentOfAbsence(se.getStudent(), se.getSubjectOffering().getSubjectName(), today);
            }
        }

        // 2. Check for overall attendance shortage (< 80%)
        List<SlotEnrollment> allEnrollments = slotEnrollmentRepository.findAll();
        for (SlotEnrollment se : allEnrollments) {
            if (se.getStudent() != null && se.getStudent().getParentEmail() != null) {
                double pct = calculateAttendancePercentage(se);
                if (pct > 0 && pct < 80.0) {
                    notifyParentOfShortage(se.getStudent(), se.getSubjectOffering().getSubjectName(), pct);
                }
            }
        }
        log.info("[ATTENDANCE AGENT] Audit completed.");
    }

    private void notifyParentOfAbsence(Student student, String subject, LocalDate date) {
        String systemPrompt = "You are a personalized university notification agent. Generate a firm but empathetic VOICE MESSAGE script for a parent. The student " + student.getFullName() + " was absent today in the subject " + subject + ".";
        String userPrompt = "Generate a 1-2 sentence voice script for the parent of " + student.getFullName() + ". Mention the date " + date + " and ask them to inform the mentor of the reason.";
        
        DecisionResponse dr = llmService.executeWithResilience(systemPrompt, userPrompt);
        saveNotification(student, "ABSENCE", dr.getReasoning());
    }

    private void notifyParentOfShortage(Student student, String subject, double percentage) {
        String systemPrompt = "You are a university academic counselor. Generate a concerned VOICE MESSAGE script for a parent. The student " + student.getFullName() + " has low attendance in " + subject + ". Current attendance is " + String.format("%.1f", percentage) + "%.";
        String userPrompt = "Generate a 2-sentence voice script for the parent. Explain that the attendance is below the 80% requirement and request immediate improvement to avoid exam ineligibility.";
        
        DecisionResponse dr = llmService.executeWithResilience(systemPrompt, userPrompt);
        saveNotification(student, "SHORTAGE", dr.getReasoning());
    }

    private void saveNotification(Student student, String type, String message) {
        ParentNotification notification = new ParentNotification();
        notification.setStudent(student);
        notification.setParentEmail(student.getParentEmail());
        notification.setType(type);
        notification.setAiMessage(message);
        notification.setSentAt(LocalDateTime.now());
        parentNotificationRepository.save(notification);
        
        log.info("[AI NOTIFICATION] Sent to {}: {}", student.getParentEmail(), message);
    }

    private double calculateAttendancePercentage(SlotEnrollment se) {
        long total = attendanceRepository.countBySlotEnrollmentId(se.getId());
        if (total == 0) return 0.0;
        long present = attendanceRepository.countBySlotEnrollmentIdAndPresentTrue(se.getId());
        return (double) present / total * 100.0;
    }
}
