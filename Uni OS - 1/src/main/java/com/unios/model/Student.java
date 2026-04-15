package com.unios.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "students")
@Data
@NoArgsConstructor
@AllArgsConstructor
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "university_id")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private University university;

    @ManyToOne
    @JoinColumn(name = "batch_id")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Batch batch;

    @Column
    private String fullName;

    @ManyToOne
    @JoinColumn(name = "program_id")
    private Program program;

    @ManyToOne
    @JoinColumn(name = "batch_program_id")
    private BatchProgram batchProgram;

    @Column
    private String department;

    @Column
    private String rollNumber;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @OneToOne
    @JoinColumn(name = "application_id")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Application application;

    @Column(columnDefinition = "TEXT")
    private String profilePhoto;

    @Column(nullable = true)
    private String parentName;

    @Column(nullable = true)
    private String parentEmail;

    @Column(nullable = true)
    private String parentPhone;

    @Column(nullable = true)
    private Integer creditsEarned = 0;

    @Column(name = "risk_level")
    private String riskLevel; // LOW, MEDIUM, HIGH
}
