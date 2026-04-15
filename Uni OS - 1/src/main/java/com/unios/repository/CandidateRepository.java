package com.unios.repository;

import com.unios.model.Candidate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CandidateRepository extends JpaRepository<Candidate, Long> {
    List<Candidate> findByUniversityId(Long universityId);
}
