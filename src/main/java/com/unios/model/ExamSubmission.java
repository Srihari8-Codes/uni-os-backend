package com.unios.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "exam_submissions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExamSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * One submission per student per subject enrollment.
     * Prevents double submission.
     */
    @OneToOne
    @JoinColumn(name = "slot_enrollment_id", nullable = false, unique = true)
    private SlotEnrollment slotEnrollment;

    /**
     * JSON string: { "questionId": "chosenOption", ... }
     * e.g. { "1": "A", "2": "B", "3": "A" }
     */
    @Column(nullable = false, length = 5000)
    private String answers;

    /**
     * Score out of 100. Score >= 50 = PASS.
     */
    @Column(nullable = false)
    private Integer score;

    @Column(nullable = false)
    private LocalDateTime submittedAt;
}
