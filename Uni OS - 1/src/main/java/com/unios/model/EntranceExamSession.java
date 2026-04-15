package com.unios.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "entrance_exam_sessions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EntranceExamSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate examDate;

    @Column(nullable = false)
    private LocalTime examTime;

    @Column(nullable = false)
    private String room;

    @Column(nullable = false)
    private Integer seatNumber;

    @ManyToOne
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @Column(nullable = true)
    private Long batchId;

    @Column(nullable = true)
    private Double score;

    @Column(nullable = true)
    private Integer examScore;
}
