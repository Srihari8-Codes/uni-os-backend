package com.unios.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalTime;

import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

@Entity
@Table(name = "applications")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Filter(name = "tenantFilter", condition = "university_id = :tenantId")
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private Double academicScore;

    @Column(nullable = false)
    private Boolean documentsVerified;

    @Column(nullable = true)
    private Double neetScore;

    @Column(nullable = true)
    private String examPassword;

    @Column(nullable = true)
    private Double schoolMarks;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "exam_hall_id")
    private Room examHall;

    @Column(nullable = true)
    private LocalTime examTimeSlot;

    @Column(nullable = false)
    private String status; // SUBMITTED, ELIGIBLE, INELIGIBLE, SELECTED, WAITLISTED, REJECTED,
                           // EXAM_SCHEDULED, EXAM_PASSED, EXAM_FAILED, COUNSELOR_SCHEDULED, COMPLETED

    @Column(columnDefinition = "TEXT")
    private String applicationData;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "university_id")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private University university;

    @ManyToOne
    @JoinColumn(name = "batch_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    private Batch batch;

    // The portal user who submitted this application (may apply for themselves or others)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicant_user_id", nullable = true)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private User applicantUser;

    @OneToOne(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)
    private Student student;

    @OneToOne(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)
    private ExamResult examResult;

    @Column(columnDefinition = "TEXT")
    private String profilePhoto;

    @Column(nullable = true)
    private String parentEmail;

    @Column(nullable = true)
    private String filePath;

    // --- OCR & Document Intelligence (v4.1) ---
    @Column(columnDefinition = "TEXT")
    private String ocrTranscript;

    private Boolean ocrVerified;

    private Double ocrAcademicScore;

    @Column(columnDefinition = "TEXT")
    private String ocrAuditLog;

    @Column(nullable = true, updatable = false)
    private java.time.LocalDateTime createdAt;

    @Column(nullable = true)
    private java.time.LocalDateTime updatedAt;

    // --- Adaptive Admissions Data ---
    @Column(columnDefinition = "TEXT", name = "extracted_marks")
    private String extractedMarks;

    @Column(nullable = true)
    private Double marks;

    @Column(nullable = true)
    private Double consistency;

    @Column(nullable = true)
    private Double variance;

    @Column(nullable = true)
    private Double entranceScore;

    @Column(nullable = true)
    private Double finalScore;

    @Column(nullable = true)
    private Double confidenceScore;

    @Column(nullable = true, columnDefinition = "TEXT")
    private String decisionReason;

    @PrePersist
    protected void onCreate() {
        createdAt = java.time.LocalDateTime.now();
        updatedAt = java.time.LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = java.time.LocalDateTime.now();
    }

    // Convenience for agents/tools
    public boolean isDocumentsVerified() {
        return Boolean.TRUE.equals(documentsVerified);
    }

    public java.time.LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
