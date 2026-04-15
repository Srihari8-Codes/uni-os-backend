package com.unios.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "graduation_candidates")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GraduationCandidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "student_id", nullable = false, unique = true)
    private Student student;

    @Column(nullable = false)
    private Integer creditsAchieved;

    @Column(nullable = false)
    private String status; // IDENTIFIED, ELIGIBLE, GRADUATED

    @Column(nullable = false)
    private LocalDateTime identifiedAt;

    private LocalDateTime processedAt;
}
