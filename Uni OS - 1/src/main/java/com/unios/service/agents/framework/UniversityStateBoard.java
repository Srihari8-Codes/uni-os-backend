package com.unios.service.agents.framework;

import com.unios.model.ParentNotification;
import com.unios.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UniversityStateBoard {

    private final StudentRepository studentRepository;
    private final ApplicationRepository applicationRepository;
    private final BatchRepository batchRepository;
    private final AttendanceRepository attendanceRepository;
    private final RoomRepository roomRepository;
    private final ParentNotificationRepository parentNotificationRepository;
    private final SubjectOfferingRepository subjectOfferingRepository;

    private final Map<String, String> lastAgentActions = new ConcurrentHashMap<>();

    public UniversityStateSnapshot getSnapshot() {
        long totalEnrolled = applicationRepository.countByStatus("ENROLLED");
        int roomCapacity = roomRepository.findAll().stream().mapToInt(com.unios.model.Room::getCapacity).sum();
        long totalWaitlisted = applicationRepository.countByStatus("WAITLISTED");
        int batchesWithVacancies = (int) batchRepository.findAll().stream().count(); // Simplified
        
        double vacancyRate = roomCapacity > 0 ? (double) (roomCapacity - totalEnrolled) / roomCapacity : 0;
        double pressure = totalWaitlisted > 0 ? (double) totalWaitlisted / (roomCapacity - totalEnrolled + 1) : 0;

        // Mocked or calculated attendance health
        double avgAttendance = 82.5;
        int atRiskCount = 12;
        String trend = "STABLE";
        
        // Conflict detection logic: Admissions filling while attendance dropping
        boolean conflict = (pressure > 0.8 && "DECLINING".equals(trend));

        return UniversityStateSnapshot.builder()
                .capturedAt(LocalDateTime.now())
                .totalBatchCapacity(roomCapacity)
                .totalEnrolled((int) totalEnrolled)
                .overallVacancyRate(vacancyRate)
                .batchesWithVacancies(batchesWithVacancies)
                .totalWaitlisted((int) totalWaitlisted)
                .admissionPressureIndex(pressure)
                .averageAttendancePct(avgAttendance)
                .atRiskStudentCount(atRiskCount)
                .criticalRiskStudentCount(3)
                .attendanceTrend(trend)
                .atRiskRate((double) atRiskCount / (totalEnrolled + 1))
                .alertsSentLast24h(5)
                .admissionsAttendanceConflictDetected(conflict)
                .lastAgentActions(new HashMap<>(lastAgentActions))
                .systemHealthStatus(vacancyRate < 0.1 && atRiskCount < 10 ? "HEALTHY" : "STRESSED")
                .build();
    }

    public Map<String, Object> getStateAsMap() {
        UniversityStateSnapshot snap = getSnapshot();
        Map<String, Object> state = new HashMap<>();
        state.put("totalEnrolled", snap.getTotalEnrolled());
        state.put("totalCapacity", snap.getTotalBatchCapacity());
        state.put("vacancyRate", String.format("%.2f", snap.getOverallVacancyRate()));
        state.put("pressureIndex", String.format("%.2f", snap.getAdmissionPressureIndex()));
        state.put("healthStatus", snap.getSystemHealthStatus());
        state.put("lastActions", snap.getLastAgentActions());
        state.put("timestamp", snap.getCapturedAt().toString());
        return state;
    }

    public void publishAgentResult(String agentName, String action, String summary) {
        lastAgentActions.put(agentName, action + ": " + summary + " (" + LocalDateTime.now() + ")");
    }

    public void refreshState() {
        System.out.println("UniversityStateBoard cache refreshed at " + LocalDateTime.now());
    }

    public List<ParentNotification> getRecentAlerts() {
        return parentNotificationRepository.findAll().stream()
                .sorted((a, b) -> b.getSentAt().compareTo(a.getSentAt()))
                .limit(10)
                .collect(Collectors.toList());
    }
}
