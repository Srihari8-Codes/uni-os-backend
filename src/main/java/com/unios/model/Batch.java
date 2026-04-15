package com.unios.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.ArrayList;

@Entity
@Table(name = "batches")
@Data
@NoArgsConstructor
@AllArgsConstructor
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Batch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Step 1: Basic Info ──────────────────────────────────────────
    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "university_id")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private University university;

    @com.fasterxml.jackson.annotation.JsonProperty("universityId")
    @jakarta.persistence.Transient
    public Long getBatchUniversityId() {
        return university != null ? university.getId() : null;
    }


    @OneToMany(mappedBy = "batch", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<BatchProgram> offeredPrograms = new ArrayList<>();

    @OneToMany(mappedBy = "batch", cascade = CascadeType.ALL, orphanRemoval = true)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private List<Application> applications = new ArrayList<>();

    @OneToOne(mappedBy = "batch", cascade = CascadeType.ALL, orphanRemoval = true)
    private ExamSchedule examSchedule;

    @OneToMany(mappedBy = "batch", cascade = CascadeType.ALL, orphanRemoval = true)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private List<Student> students = new ArrayList<>();

    @OneToMany(mappedBy = "batch", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Enrollment> enrollments = new ArrayList<>();

    @OneToMany(mappedBy = "batch", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SubjectOffering> subjectOfferings = new ArrayList<>();

    @Column(nullable = false)
    private Integer startYear;

    @Column(nullable = false)
    private Integer endYear;

    private Integer intakeCapacity;
    private Integer duration;
    private String batchCode;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** JSON array: ["Computer Science","Electronics","Civil Engineering"] */
    @Column(columnDefinition = "TEXT")
    private String departments;

    // Remaining steps (Admission, Financial) stay at batch level or move to check logic

    // ── Step 3: Admission Rules ────────────────────────────────────
    private Integer seatCapacity;
    private Integer waitlistCapacity;
    private Double minAcademicCutoff;
    private String entranceExam;
    private Integer examWeightage;

    /** JSON array: ["High School Mark Sheets","Entrance Exam Scorecard",...] */
    @Column(columnDefinition = "TEXT")
    private String documentRequirements;

    private String applicationDeadline;

    // ── Step 4: Financial Policies ─────────────────────────────────
    private Double tuitionFee;
    private Double registrationFee;
    private Double semesterFee;
    private Double lateFee;
    private Double attendanceShortage;
    private Double libraryDues;

    /** JSON array: [{name,type,value,conditions},...] */
    @Column(columnDefinition = "TEXT")
    private String scholarships;

    // ── Status ─────────────────────────────────────────────────────
    @Column(nullable = false)
    private String status; // DRAFT, CREATED, ADMISSIONS_OPEN, ACTIVE
}
