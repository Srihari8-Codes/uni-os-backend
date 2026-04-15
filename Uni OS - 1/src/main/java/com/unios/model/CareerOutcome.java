package com.unios.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "career_outcomes")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CareerOutcome {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(nullable = false)
    private String outcomeType; // PLACEMENT, HIGHER_STUDIES, ENTREPRENEURSHIP

    private String description;
}
