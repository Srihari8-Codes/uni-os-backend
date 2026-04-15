package com.unios.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "student_outcomes")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentOutcome {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(nullable = true)
    private Double attendancePct;

    @Column(nullable = true)
    private Double firstExamScore;
}
