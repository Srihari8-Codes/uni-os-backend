package com.unios.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "slot_enrollments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlotEnrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "student_id")
    private Student student;

    @ManyToOne
    @JoinColumn(name = "subject_offering_id")
    private SubjectOffering subjectOffering;

    private String enrollmentDate;
    private String status; // ENROLLED, COMPLETED, DROPPED

    @Builder.Default
    @Column(nullable = true)
    private Boolean examEligible = true;

    private Double examMarks;
    @Builder.Default
    private Integer creditsEarned = 0;

    public void setMarks(int marks) {
        this.examMarks = (double) marks;
    }
}
