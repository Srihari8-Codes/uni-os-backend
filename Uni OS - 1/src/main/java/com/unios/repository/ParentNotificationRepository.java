package com.unios.repository;

import com.unios.model.ParentNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ParentNotificationRepository extends JpaRepository<ParentNotification, Long> {
    List<ParentNotification> findByStudentId(Long studentId);

    // For cooldown de-duplication: alerts sent after a given timestamp
    List<ParentNotification> findByStudentIdAndSentAtAfter(Long studentId, LocalDateTime sentAfter);
}
