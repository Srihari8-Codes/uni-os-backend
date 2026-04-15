package com.unios.repository;

import com.unios.model.QuestionBank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionBankRepository extends JpaRepository<QuestionBank, Long> {
    List<QuestionBank> findBySubjectName(String subjectName);
}
