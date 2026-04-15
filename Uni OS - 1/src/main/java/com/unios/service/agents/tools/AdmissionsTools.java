package com.unios.service.agents.tools;

import com.unios.model.Batch;
import com.unios.repository.ApplicationRepository;
import com.unios.repository.BatchRepository;
import com.unios.service.agents.framework.AgentTool;
import com.unios.service.agents.framework.ToolContext;
import com.unios.service.agents.framework.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AdmissionsTools — INTELLIGENT EDITION
 *
 * Previously: Simple DB count, produced a flat vacancy string.
 * Now: Ranked vacancy analysis that outputs:
 *   1. Vacancy urgency score per batch (based on vacancy %, deadline proximity)
 *   2. Availability of waitlisted candidates
 *   3. Priority order of which batches to act on first
 *
 * The output includes reasoning and structured actionData so the LLM
 * can immediately decide WHICH batch to call PROMOTE_WAITLIST on.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdmissionsTools implements AgentTool {

    private final BatchRepository batchRepository;
    private final ApplicationRepository applicationRepository;

    @Override
    public String name() {
        return "ADMISSIONS_AUDIT";
    }

    @Override
    public String description() {
        return "Analyzes all active batches for vacancies. Returns a ranked list of batches with urgency " +
               "scores, waitlist availability, and deadline proximity — ready for PROMOTE_WAITLIST action.";
    }

    @Override
    public ToolResult executeWithContext(ToolContext context) {
        List<Batch> activeBatches = batchRepository.findAll().stream()
                .filter(b -> "ACTIVE".equalsIgnoreCase(b.getStatus()))
                .collect(Collectors.toList());

        if (activeBatches.isEmpty()) {
            return ToolResult.builder()
                    .summary("No active batches found.")
                    .reasoning("batchRepository returned no ACTIVE batches.")
                    .confidence(1.0).status("NO_ACTION_NEEDED").build();
        }

        List<BatchVacancyProfile> profiles = new ArrayList<>();
        for (Batch batch : activeBatches) {
            long enrolled = applicationRepository.countByBatchIdAndStatus(batch.getId(), "ENROLLED")
                          + applicationRepository.countByBatchIdAndStatus(batch.getId(), "COMPLETED");
            long waitlisted = applicationRepository.countByBatchIdAndStatus(batch.getId(), "WAITLISTED");
            int capacity = batch.getSeatCapacity() != null ? batch.getSeatCapacity() : 0;
            int vacancy = (int) (capacity - enrolled);

            if (vacancy <= 0) continue; // batch is full

            double vacancyRate = capacity == 0 ? 0 : (double) vacancy / capacity;
            
            LocalDate deadline = null;
            if (batch.getApplicationDeadline() != null) {
                try {
                    deadline = LocalDate.parse(batch.getApplicationDeadline());
                } catch (Exception ignored) {}
            }
            
            double deadlineUrgency = computeDeadlineUrgency(deadline);
            double urgencyScore = (vacancyRate * 0.6) + (deadlineUrgency * 0.4);

            String fillability = waitlisted > 0
                    ? (waitlisted >= vacancy ? "FULLY_FILLABLE" : "PARTIALLY_FILLABLE")
                    : "NO_WAITLIST";

            profiles.add(new BatchVacancyProfile(
                    batch.getId(), batch.getName(), vacancy, (int) enrolled, capacity,
                    (int) waitlisted, vacancyRate, urgencyScore, fillability, deadline
            ));
        }

        if (profiles.isEmpty()) {
            return ToolResult.builder()
                    .summary("All active batches are at full capacity. No action needed.")
                    .reasoning("Every active batch has 0 vacancies.")
                    .confidence(1.0).status("NO_ACTION_NEEDED").requiresFollowUp(false).build();
        }

        // Sort by urgency descending
        profiles.sort(Comparator.comparingDouble(BatchVacancyProfile::urgencyScore).reversed());

        BatchVacancyProfile top = profiles.get(0);

        List<Map<String, Object>> rankedList = profiles.stream()
                .map(p -> Map.<String, Object>of(
                        "batchId", p.batchId(),
                        "name", p.name(),
                        "vacancies", p.vacancy(),
                        "waitlisted", p.waitlisted(),
                        "urgencyScore", String.format("%.2f", p.urgencyScore()),
                        "fillability", p.fillability()
                ))
                .collect(Collectors.toList());

        String reasoning = String.format(
                "Scanned %d active batches. Found %d with vacancies. " +
                "Top priority: '%s' (ID=%d) with %d vacancies (urgency=%.2f, fillability=%s). " +
                "Urgency formula: vacancy_rate(60%%) + deadline_proximity(40%%).",
                activeBatches.size(), profiles.size(),
                top.name(), top.batchId(), top.vacancy(), top.urgencyScore(), top.fillability());

        return ToolResult.builder()
                .summary(String.format("Found %d batch(es) with vacancies. Highest urgency: '%s' (%d seats open).",
                        profiles.size(), top.name(), top.vacancy()))
                .reasoning(reasoning)
                .confidence(0.95)
                .actionData(Map.of(
                        "topBatchId", top.batchId(),
                        "topBatchName", top.name(),
                        "topVacancy", top.vacancy(),
                        "totalVacantBatches", profiles.size()
                ))
                .rankedAlternatives(rankedList)
                .requiresFollowUp(!top.fillability().equals("NO_WAITLIST"))
                .suggestedNextAction(top.fillability().equals("NO_WAITLIST") ? "FINALIZE" : "PROMOTE_WAITLIST")
                .status("SUCCESS")
                .build();
    }

    /**
     * Urgency from deadline: 1.0 if deadline is today or past,
     * 0.0 if more than 30 days away.
     */
    private double computeDeadlineUrgency(java.time.LocalDate deadline) {
        if (deadline == null) return 0.3; // unknown deadline — moderate urgency
        long daysLeft = LocalDate.now().until(deadline).getDays();
        if (daysLeft <= 0) return 1.0;
        if (daysLeft >= 30) return 0.0;
        return 1.0 - (daysLeft / 30.0);
    }

    private record BatchVacancyProfile(Long batchId, String name, int vacancy, int enrolled,
                                        int capacity, int waitlisted, double vacancyRate,
                                        double urgencyScore, String fillability,
                                        java.time.LocalDate deadline) {}
}
