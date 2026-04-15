package com.unios.repository;

import com.unios.model.FailedTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FailedTaskRepository extends JpaRepository<FailedTask, Long> {
}
