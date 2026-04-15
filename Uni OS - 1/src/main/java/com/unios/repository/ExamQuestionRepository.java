package com.unios.repository;

import com.unios.model.ExamQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExamQuestionRepository extends JpaRepository<ExamQuestion, Long> {
    List<ExamQuestion> findBySubjectOfferingId(Long subjectOfferingId);

    boolean existsBySubjectOfferingId(Long subjectOfferingId);

    int countBySubjectOfferingId(Long subjectOfferingId);
}
