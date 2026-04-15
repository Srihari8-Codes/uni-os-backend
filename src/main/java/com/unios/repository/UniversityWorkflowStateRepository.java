package com.unios.repository;

import com.unios.model.UniversityWorkflowState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UniversityWorkflowStateRepository extends JpaRepository<UniversityWorkflowState, Long> {
    Optional<UniversityWorkflowState> findByApplicationId(Long applicationId);
}
