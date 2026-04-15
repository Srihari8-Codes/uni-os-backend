package com.unios.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "subject_offerings")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubjectOffering {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String subjectName;

    @Column(nullable = false)
    private String slot; // A–Z

    @Column(nullable = false)
    private Integer capacity;

    @Column(nullable = false)
    private Integer credits;

    private String prerequisite; // Nullable

    @ManyToOne
    @JoinColumn(name = "faculty_id", nullable = false)
    private Faculty faculty;

    @ManyToOne
    @JoinColumn(name = "batch_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Batch batch;

    @Column(nullable = false)
    private Boolean active;

    @Column(nullable = false, columnDefinition = "varchar(255) default 'APPROVED'")
    private String status = "APPROVED"; // DRAFT, PENDING_APPROVAL, APPROVED, REJECTED

    @Column(nullable = false, columnDefinition = "boolean default false")
    private Boolean lockedForExam = false;

    private String room;
}
