package com.unios.service;

import com.unios.model.ExamHall;
import com.unios.model.Goal;
import com.unios.repository.ExamHallRepository;
import com.unios.repository.GoalRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DataSeedingService {

    private final ExamHallRepository examHallRepository;
    private final GoalRepository goalRepository;

    public DataSeedingService(ExamHallRepository examHallRepository,
                              GoalRepository goalRepository) {
        this.examHallRepository = examHallRepository;
        this.goalRepository = goalRepository;
    }

    @PostConstruct
    @Transactional
    public void seedExamHalls() {
        if (examHallRepository.count() == 0) {
            System.out.println("[SEEDING] Generating 100 Exam Halls...");
            for (int i = 1; i <= 100; i++) {
                ExamHall hall = new ExamHall();
                hall.setName("Hall-" + String.format("%03d", i));
                hall.setCapacity(40 + (int)(Math.random() * 20)); // Random capacity 40-60
                examHallRepository.save(hall);
            }
            System.out.println("[SEEDING] 100 Exam Halls created.");
        }
    }

    @PostConstruct
    @Transactional
    public void seedInitialGoals() {
        if (goalRepository.count() == 0) {
            System.out.println("[SEEDING] Seeding initial University Goals...");

            goalRepository.save(Goal.builder()
                    .name("Seat Utilization Guardian")
                    .goalStatement("Fill every seat with the best possible students, and don't let a single spot go to waste. " +
                            "Audit all active batches for vacancies and promote the highest-ranked waitlisted candidates automatically.")
                    .agentName("AdmissionsAgent")
                    .category("ADMISSIONS")
                    .type(Goal.GoalType.ADMISSIONS)
                    .priority(90)
                    .urgencyScore(0.5)
                    .status(Goal.GoalStatus.ACTIVE)
                    .build());

            goalRepository.save(Goal.builder()
                    .name("Attendance Exam Eligibility Guardian")
                    .goalStatement("Ensure as many students as possible enter exams by maintaining 80% attendance. " +
                            "Detect at-risk students early, escalate alerts to parents, and prevent exam ineligibility.")
                    .agentName("AttendanceGuardian")
                    .category("ATTENDANCE")
                    .type(Goal.GoalType.ATTENDANCE)
                    .priority(85)
                    .urgencyScore(0.5)
                    .status(Goal.GoalStatus.ACTIVE)
                    .build());

            System.out.println("[SEEDING] Initial University Goals seeded.");
        }
    }
}
