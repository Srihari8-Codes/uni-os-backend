package com.unios.repository;

import com.unios.model.EmailLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmailLogRepository extends JpaRepository<EmailLog, Long> {
    List<EmailLog> findByStatus(String status);
}
