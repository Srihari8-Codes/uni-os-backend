package com.unios.repository;

import com.unios.model.Student;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long> {
    Optional<Student> findByUserId(Long userId);

    Optional<Student> findByUserEmail(String email);

    default Optional<Student> findByEmail(String email) {
        return findByUserEmail(email);
    }

    java.util.List<Student> findByBatchId(Long batchId);
    
    Optional<Student> findByApplicationId(Long applicationId);

    long countByUniversityId(Long universityId);
}
