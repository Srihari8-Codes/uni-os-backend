package com.unios.repository;

import com.unios.model.SubjectOffering;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface SubjectOfferingRepository extends JpaRepository<SubjectOffering, Long> {
    List<SubjectOffering> findByBatchId(Long batchId);

    Optional<SubjectOffering> findByBatchIdAndSlot(Long batchId, String slot);

    List<SubjectOffering> findByActiveTrue();

    List<SubjectOffering> findByFacultyId(Long facultyId);

    List<SubjectOffering> findByStatus(String status);
}
