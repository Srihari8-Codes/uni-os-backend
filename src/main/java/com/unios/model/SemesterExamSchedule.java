package com.unios.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "semester_exam_schedules")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SemesterExamSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "subject_offering_id", nullable = false)
    private SubjectOffering subjectOffering;

    private LocalDate examDate;

    private String room;

    /**
     * Status flow: PENDING_ADMIN → APPROVED → COMPLETED
     */
    private String status;

    @Column(length = 5000)
    private String hallAllocations; // JSON seat assignments per student rollNumber

    private LocalDateTime adminApprovedAt;
}
