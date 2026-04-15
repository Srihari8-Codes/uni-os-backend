package com.unios.service.scheduler;

import com.unios.domain.events.AcademicCompletionEvent;
import com.unios.model.SlotEnrollment;
import com.unios.repository.AttendanceRepository;
import com.unios.repository.SlotEnrollmentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Random;

@Service
@Slf4j
public class AcademicSchedulerService {

    private final SlotEnrollmentRepository slotEnrollmentRepository;
    private final AttendanceRepository attendanceRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Random random = new Random();

    public AcademicSchedulerService(SlotEnrollmentRepository slotEnrollmentRepository,
            AttendanceRepository attendanceRepository,
            ApplicationEventPublisher eventPublisher) {
        this.slotEnrollmentRepository = slotEnrollmentRepository;
        this.attendanceRepository = attendanceRepository;
        this.eventPublisher = eventPublisher;
    }

    // @Scheduled(fixedRate = 60000) // Every 1 minute
    @Transactional
    public void runAcademicCycle() {
        // log.info("[SCHEDULER] Running Academic Cycle...");
        // Auto-completion logic disabled to favor manual exam-based lifecycle.
    }
}
