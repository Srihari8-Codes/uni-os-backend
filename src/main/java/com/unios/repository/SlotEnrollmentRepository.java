package com.unios.repository;

import com.unios.model.SlotEnrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SlotEnrollmentRepository extends JpaRepository<SlotEnrollment, Long> {
    List<SlotEnrollment> findByStudentId(Long studentId);

    Optional<SlotEnrollment> findByStudentIdAndSubjectOfferingId(Long studentId, Long subjectOfferingId);

    List<SlotEnrollment> findByStudentIdAndStatus(Long studentId, String status);

    long countBySubjectOfferingId(Long subjectOfferingId);

    List<SlotEnrollment> findBySubjectOfferingIdAndStatus(Long subjectOfferingId, String status);

    long countBySubjectOfferingIdAndStatus(Long subjectOfferingId, String status);

    long countByStatus(String status);
}
