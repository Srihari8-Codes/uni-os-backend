package com.unios.repository;

import com.unios.model.Faculty;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FacultyRepository extends JpaRepository<Faculty, Long> {
    Optional<Faculty> findByUserId(Long userId);

    Optional<Faculty> findByUserEmail(String email);

    java.util.List<Faculty> findByDepartment(String department);

    long countByUniversityId(Long universityId);
}
