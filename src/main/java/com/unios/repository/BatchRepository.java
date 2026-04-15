package com.unios.repository;

import com.unios.model.Batch;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BatchRepository extends JpaRepository<Batch, Long> {
    List<Batch> findByStatus(String status);
    List<Batch> findByUniversityId(Long universityId);
    List<Batch> findByUniversityIdAndStatus(Long universityId, String status);
}
