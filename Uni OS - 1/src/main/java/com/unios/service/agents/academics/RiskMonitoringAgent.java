package com.unios.service.agents.academics;

import com.unios.model.AgentDecisionLog;
import com.unios.model.SlotEnrollment;
import com.unios.model.Student;
import com.unios.repository.AgentDecisionLogRepository;
import com.unios.repository.AttendanceRepository;
import com.unios.repository.SlotEnrollmentRepository;
import com.unios.repository.StudentRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

@Service
public class RiskMonitoringAgent {

    private final SlotEnrollmentRepository slotEnrollmentRepository;
    private final AttendanceRepository attendanceRepository;
    private final StudentRepository studentRepository;
    private final AgentDecisionLogRepository agentDecisionLogRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final com.unios.service.llm.ReasoningEngineService reasoningEngineService;
    private final com.unios.service.agents.framework.AsyncAgentQueue asyncQueue;
    private final com.unios.repository.AgentTaskRepository agentTaskRepository;

    public RiskMonitoringAgent(SlotEnrollmentRepository slotEnrollmentRepository,
            AttendanceRepository attendanceRepository,
            StudentRepository studentRepository,
            AgentDecisionLogRepository agentDecisionLogRepository,
            ApplicationEventPublisher eventPublisher,
            com.unios.service.llm.ReasoningEngineService reasoningEngineService,
            com.unios.service.agents.framework.AsyncAgentQueue asyncQueue,
            com.unios.repository.AgentTaskRepository agentTaskRepository) {
        this.slotEnrollmentRepository = slotEnrollmentRepository;
        this.attendanceRepository = attendanceRepository;
        this.studentRepository = studentRepository;
        this.agentDecisionLogRepository = agentDecisionLogRepository;
        this.eventPublisher = eventPublisher;
        this.reasoningEngineService = reasoningEngineService;
        this.asyncQueue = asyncQueue;
        this.agentTaskRepository = agentTaskRepository;
    }

    @Scheduled(cron = "0 */5 * * * *")
    @Transactional
    public void monitorAllStudents() {
        System.out.println("[Agentic AI] Starting scheduled attendance risk monitoring sweep...");
        List<Student> students = studentRepository.findAll();
        for (Student student : students) {
            evaluateRisk(student.getId());
        }
        System.out.println("[Agentic AI] Risk monitoring sweep completed.");
    }

    @Transactional
    public String evaluateRisk(Long studentId) {
        List<SlotEnrollment> enrollments = slotEnrollmentRepository.findByStudentIdAndStatus(studentId, "ENROLLED");
        List<SlotEnrollment> failed = slotEnrollmentRepository.findByStudentIdAndStatus(studentId, "FAILED");

        if (enrollments.isEmpty() && failed.isEmpty()) {
            return "LOW";
        }

        Student student = studentRepository.findById(studentId).orElse(null);
        if (student == null) return "LOW";

        long totalClasses = 0;
        long presentClasses = 0;
        for (SlotEnrollment enrollment : enrollments) {
            totalClasses += attendanceRepository.countBySlotEnrollmentId(enrollment.getId());
            presentClasses += attendanceRepository.countBySlotEnrollmentIdAndPresentTrue(enrollment.getId());
        }
        double attendanceRate = totalClasses == 0 ? 1.0 : (double) presentClasses / totalClasses;

        String goal = String.format(
            "Academically support Student %s (ID: %d). Current Attendance: %.1f%%. Failures: %d. " +
            "DECIDE: If risk is HIGH, use EmailTriggerTool to warn them. If MEDIUM, flag for counseling. " +
            "Goal achieved when an intervention is performed or risk determined to be LOW.", 
            student.getFullName(), studentId, attendanceRate * 100, failed.size()
        );

        Map<String, String> context = new HashMap<>(); // Change to String/String for AgentTask context map
        context.put("studentId", String.valueOf(studentId));
        context.put("email", student.getUser().getEmail());
        context.put("attendanceRate", String.valueOf(attendanceRate));

        com.unios.model.AgentTask task = com.unios.model.AgentTask.builder()
            .taskId(UUID.randomUUID().toString())
            .agentName("RiskMonitoringAgent")
            .entityType("Student")
            .entityId(studentId)
            .goal(goal)
            .context(context)
            .status("PENDING")
            .retryCount(0)
            .build();

        // Enqueue via framework
        asyncQueue.enqueue(com.unios.service.agents.framework.AgentWorkTask.builder()
            .taskId(task.getTaskId())
            .agentName(task.getAgentName())
            .entityType(task.getEntityType())
            .entityId(task.getEntityId())
            .goal(task.getGoal())
            .context(task.getContext())
            .status("PENDING")
            .build());

        return "PENDING_AGENT_EVALUATION";
    }
}
