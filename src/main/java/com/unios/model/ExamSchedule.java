package com.unios.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "exam_schedules")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExamSchedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "batch_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Batch batch;

    private LocalDateTime examDate;
    private Integer totalCapacity;

    @Column(columnDefinition = "TEXT")
    private String hallAllocations;

    private String status; // GENERATED, APPROVED
}
