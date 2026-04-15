package com.unios.repository;

import com.unios.model.AttendanceIntervention;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AttendanceInterventionRepository extends JpaRepository<AttendanceIntervention, Long> {

    Optional<AttendanceIntervention> findFirstByStudentIdAndIsActiveTrue(Long studentId);
}
