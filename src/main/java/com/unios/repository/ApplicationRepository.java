package com.unios.repository;

import com.unios.model.Application;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long> {
    List<Application> findByBatchId(Long batchId);
    long countByBatchId(Long batchId);

    List<Application> findByBatchIdAndStatus(Long batchId, String status);
    Optional<Application> findByEmail(String email);
    Optional<Application> findFirstByEmailAndStatus(String email, String status);

    List<Application> findByBatchIdAndStatusIn(Long batchId, java.util.Collection<String> statuses);

    List<Application> findByStatusAndExamHallId(String status, Long examHallId);

    long countByStatusAndExamHallId(String status, Long examHallId);

    List<Application> findByStatus(String status);

    long countByBatchIdAndStatus(Long batchId, String status);

    long countByStatus(String status);

    long countByBatchIdAndStatusIn(Long batchId, java.util.Collection<String> statuses);

    List<Application> findByStatusIn(java.util.Collection<String> statuses);

    List<Application> findByUniversityId(Long universityId);

    long countByUniversityId(Long universityId);

    List<Application> findByApplicantUserId(Long userId);
}
