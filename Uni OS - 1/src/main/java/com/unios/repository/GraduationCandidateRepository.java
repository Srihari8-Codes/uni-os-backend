package com.unios.repository;

import com.unios.model.GraduationCandidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface GraduationCandidateRepository extends JpaRepository<GraduationCandidate, Long> {
    Optional<GraduationCandidate> findByStudentId(Long studentId);

    List<GraduationCandidate> findByStatus(String status);
}
