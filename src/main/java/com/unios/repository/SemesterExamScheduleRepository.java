package com.unios.repository;

import com.unios.model.SemesterExamSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SemesterExamScheduleRepository extends JpaRepository<SemesterExamSchedule, Long> {
    Optional<SemesterExamSchedule> findBySubjectOfferingId(Long subjectOfferingId);

    List<SemesterExamSchedule> findByStatus(String status);
}
