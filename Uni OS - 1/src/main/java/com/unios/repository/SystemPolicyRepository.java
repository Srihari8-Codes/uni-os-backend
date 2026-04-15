package com.unios.repository;

import com.unios.model.SystemPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SystemPolicyRepository extends JpaRepository<SystemPolicy, String> {
    Optional<SystemPolicy> findByPolicyKey(String policyKey);
}
