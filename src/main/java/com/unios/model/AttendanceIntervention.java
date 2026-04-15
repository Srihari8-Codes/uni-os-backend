package com.unios.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "attendance_interventions", indexes = {
    @Index(name = "idx_student_intervention", columnList = "student_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceIntervention {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_level", nullable = false)
    private InterventionLevel currentLevel;

    @Column(name = "last_action_date", nullable = false)
    private LocalDateTime lastActionDate;

    @Column(name = "baseline_attendance")
    private Double baselineAttendance;

    @Column(name = "improvement_score")
    @Builder.Default
    private Double improvementScore = 0.0;
    
    @Column(name = "student_feedback", columnDefinition = "TEXT")
    private String studentFeedback;
    
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public enum InterventionLevel {
        NONE,                // Baseline > 80%
        EMAIL_STUDENT,       // < 80%
        SMS_STUDENT,         // No improvement after 3 days
        EMAIL_PARENT,        // No improvement after 7 days
        DIRECT_CALL,         // Critical threshold < 60% or ignored parent
        ACADEMIC_PROBATION   // Complete failure to respond
    }
}
