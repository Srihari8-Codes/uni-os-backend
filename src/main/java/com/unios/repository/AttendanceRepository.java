package com.unios.repository;

import com.unios.model.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
    List<Attendance> findByDate(LocalDate date);
    List<Attendance> findByDateAndPresentFalse(LocalDate date);
    List<Attendance> findBySlotEnrollmentIdAndDate(Long slotEnrollmentId, LocalDate date);

    long countBySlotEnrollmentId(Long slotEnrollmentId);
    long countBySlotEnrollmentIdAndPresentTrue(Long slotEnrollmentId);

    long countBySlotEnrollment_SubjectOfferingId(Long subjectOfferingId);
    long countBySlotEnrollment_SubjectOfferingIdAndPresentTrue(Long subjectOfferingId);

    // ── Trend analysis: date-windowed counts ────────────────────────────────
    long countBySlotEnrollmentIdAndDateAfter(Long slotEnrollmentId, LocalDate date);
    long countBySlotEnrollmentIdAndDateAfterAndPresentTrue(Long slotEnrollmentId, LocalDate date);

    // ── Streak analysis: ordered by date descending ──────────────────────────
    List<Attendance> findBySlotEnrollmentIdOrderByDateDesc(Long slotEnrollmentId);
}
