package com.unios.repository;

import com.unios.model.JoinRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JoinRequestRepository extends JpaRepository<JoinRequest, Long> {
    List<JoinRequest> findByUniversityId(Long universityId);
    List<JoinRequest> findByUniversityIdAndStatus(Long universityId, JoinRequest.Status status);
}
