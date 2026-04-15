package com.unios.service.agents.framework;

import com.unios.model.Goal;
import com.unios.repository.ApplicationRepository;
import com.unios.repository.AttendanceRepository;
import com.unios.repository.BatchRepository;
import com.unios.repository.GoalRepository;
import com.unios.repository.SlotEnrollmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * GoalManager — the strategic intelligence layer of University OS.
 *
 * Replaces all hardcoded goal strings with dynamic, database-backed objectives
 * that are continuously re-evaluated against live system state.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GoalManager {

    private final GoalRepository goalRepository;
    private final BatchRepository batchRepository;
    private final ApplicationRepository applicationRepository;
    private final AttendanceRepository attendanceRepository;
    private final SlotEnrollmentRepository slotEnrollmentRepository;

    @Transactional
    public List<Goal> getGoalsForCurrentCycle() {
        recomputeUrgencyScores();

        List<Goal> eligible = goalRepository.findTopActiveGoalsByWeight()
                .stream()
                .filter(this::areDependenciesMet)
                .collect(Collectors.toList());

        List<Goal> resolved = resolveConflicts(eligible);

        log.info("[GOAL MANAGER] {} goals selected for this cycle.", resolved.size());
        resolved.forEach(g -> log.info("  → [{}] {} (priority={}, urgency={})",
                g.getCategory(), g.getName(), g.getPriority(), g.getUrgencyScore()));

        return resolved;
    }

    private void recomputeUrgencyScores() {
        List<Goal> goals = goalRepository.findByStatusOrderByPriorityDescUrgencyScoreDesc(Goal.GoalStatus.ACTIVE);
        for (Goal goal : goals) {
            double urgency = switch (goal.getCategory() != null ? goal.getCategory() : "") {
                case "ADMISSIONS"  -> computeAdmissionsUrgency();
                case "ATTENDANCE"  -> computeAttendanceUrgency();
                case "ACADEMICS"   -> 0.5;
                case "HR"          -> 0.3;
                default            -> 0.4;
            };
            goal.setUrgencyScore(urgency);
            goalRepository.save(goal);
        }
        log.debug("[GOAL MANAGER] Urgency scores refreshed.");
    }

    private double computeAdmissionsUrgency() {
        try {
            var batches = batchRepository.findAll().stream()
                    .filter(b -> "ACTIVE".equalsIgnoreCase(b.getStatus()))
                    .collect(Collectors.toList());
            if (batches.isEmpty()) return 0.0;

            long totalCapacity = batches.stream()
                    .mapToLong(b -> b.getSeatCapacity() != null ? b.getSeatCapacity() : 0).sum();
            long totalEnrolled = batches.stream().mapToLong(b ->
                    applicationRepository.countByBatchIdAndStatus(b.getId(), "ENROLLED") +
                    applicationRepository.countByBatchIdAndStatus(b.getId(), "COMPLETED")).sum();

            double vacancyRate = totalCapacity == 0 ? 0 : (double)(totalCapacity - totalEnrolled) / totalCapacity;
            return Math.min(1.0, Math.max(0.0, vacancyRate));
        } catch (Exception e) {
            log.warn("[GOAL MANAGER] Could not compute admissions urgency: {}", e.getMessage());
            return 0.5;
        }
    }

    private double computeAttendanceUrgency() {
        try {
            List<?> all = slotEnrollmentRepository.findAll();
            if (all.isEmpty()) return 0.0;

            long atRisk = all.stream()
                    .filter(se -> {
                        if (se instanceof com.unios.model.SlotEnrollment slot) {
                            long total = attendanceRepository.countBySlotEnrollmentId(slot.getId());
                            if (total == 0) return false;
                            long present = attendanceRepository.countBySlotEnrollmentIdAndPresentTrue(slot.getId());
                            return ((double) present / total * 100.0) < 80.0;
                        }
                        return false;
                    }).count();

            return Math.min(1.0, (double) atRisk / all.size());
        } catch (Exception e) {
            log.warn("[GOAL MANAGER] Could not compute attendance urgency: {}", e.getMessage());
            return 0.5;
        }
    }

    private List<Goal> resolveConflicts(List<Goal> goals) {
        return goals.stream()
                .collect(Collectors.groupingBy(g -> g.getAgentName() != null ? g.getAgentName() : "UNKNOWN"))
                .values().stream()
                .map(agentGoals -> agentGoals.stream()
                        .max(Comparator.comparingDouble(g -> g.getPriority() * g.getUrgencyScore()))
                        .orElseThrow())
                .sorted(Comparator.comparingDouble((Goal g) -> g.getPriority() * g.getUrgencyScore()).reversed())
                .collect(Collectors.toList());
    }

    private boolean areDependenciesMet(Goal goal) {
        if (goal.getDependsOnGoalIds() == null || goal.getDependsOnGoalIds().isBlank()) {
            return true;
        }
        for (String idStr : goal.getDependsOnGoalIds().split(",")) {
            try {
                Long depId = Long.parseLong(idStr.trim());
                Optional<Goal> dep = goalRepository.findById(depId);
                if (dep.isEmpty() || Goal.GoalStatus.COMPLETED != dep.get().getStatus()) {
                    log.info("[GOAL MANAGER] Goal '{}' blocked — dependency {} not satisfied.", goal.getName(), depId);
                    return false;
                }
            } catch (NumberFormatException ignored) {}
        }
        return true;
    }

    public Goal createGoal(Goal goal) {
        goal.setStatus(Goal.GoalStatus.ACTIVE);
        return goalRepository.save(goal);
    }

    public Optional<Goal> getGoal(Long id) {
        return goalRepository.findById(id);
    }

    public List<Goal> getAllGoals() {
        return goalRepository.findAll();
    }

    @Transactional
    public Goal updateGoal(Long id, Goal updates) {
        Goal existing = goalRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Goal not found: " + id));
        existing.setName(updates.getName());
        existing.setGoalStatement(updates.getGoalStatement());
        existing.setPriority(updates.getPriority());
        existing.setCategory(updates.getCategory());
        existing.setAgentName(updates.getAgentName());
        existing.setDependsOnGoalIds(updates.getDependsOnGoalIds());
        return goalRepository.save(existing);
    }

    @Transactional
    public void abandonGoal(Long id, String reason) {
        goalRepository.findById(id).ifPresent(goal -> {
            goal.setStatus(Goal.GoalStatus.ABANDONED);
            goal.setAbandonReason(reason);
            goalRepository.save(goal);
            log.info("[GOAL MANAGER] Goal '{}' ABANDONED. Reason: {}", goal.getName(), reason);
        });
    }

    @Transactional
    public void markGoalPursued(Long id) {
        goalRepository.findById(id).ifPresent(goal -> {
            goal.setLastPursuedAt(LocalDateTime.now());
            goalRepository.save(goal);
        });
    }

    @Transactional
    public void completeGoal(Long id) {
        goalRepository.findById(id).ifPresent(goal -> {
            goal.setStatus(Goal.GoalStatus.COMPLETED);
            goalRepository.save(goal);
            log.info("[GOAL MANAGER] Goal '{}' marked COMPLETED.", goal.getName());
        });
    }
}
