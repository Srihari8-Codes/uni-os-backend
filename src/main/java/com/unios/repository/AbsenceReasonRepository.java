package com.unios.repository;

import com.unios.model.AbsenceReason;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AbsenceReasonRepository extends JpaRepository<AbsenceReason, Long> {
    List<AbsenceReason> findByStudentId(Long studentId);
    List<AbsenceReason> findByTimestampAfter(LocalDateTime timestamp);
    List<AbsenceReason> findByStudentIdAndSubjectNameAndTimestampAfter(Long studentId, String subjectName, LocalDateTime timestamp);
}
