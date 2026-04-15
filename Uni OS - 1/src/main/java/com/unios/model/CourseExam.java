package com.unios.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "course_exams")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseExam {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "subject_offering_id", nullable = false)
    private SubjectOffering subjectOffering;

    @ManyToOne
    @JoinColumn(name = "exam_hall_id", nullable = false)
    private ExamHall examHall;

    @Column(nullable = false)
    private LocalDateTime startTime;

    @Column(nullable = false)
    private Integer durationMinutes = 60;

    @Column(columnDefinition = "TEXT")
    private String questionsJson; // AI-generated 10 MCQs

    @Column(nullable = false)
    private String status = "SCHEDULED"; // SCHEDULED, ONGOING, COMPLETED
}
