package com.unios.repository;

import com.unios.model.CourseExam;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CourseExamRepository extends JpaRepository<CourseExam, Long> {
    Optional<CourseExam> findBySubjectOfferingId(Long subjectOfferingId);
}
