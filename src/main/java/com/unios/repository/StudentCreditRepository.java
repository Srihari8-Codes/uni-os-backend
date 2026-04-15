package com.unios.repository;

import com.unios.model.StudentCredit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface StudentCreditRepository extends JpaRepository<StudentCredit, Long> {
    Optional<StudentCredit> findByStudentId(Long studentId);
}
