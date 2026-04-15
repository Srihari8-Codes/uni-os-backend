package com.unios.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "parent_notifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParentNotification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(nullable = false)
    private String parentEmail;

    @Column(nullable = false)
    private String type; // ABSENCE, SHORTAGE

    @Column(columnDefinition = "TEXT", nullable = false)
    private String aiMessage;

    @Column(nullable = false)
    private LocalDateTime sentAt;

    // --- Interaction Feedback (v4.1) ---
    @Column(columnDefinition = "TEXT")
    private String parentReply;

    private Double sentimentScore;

    private String rootCause; // e.g., SICKNESS, TRANSPORT, PERSONAL
}
