package com.unios.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "absence_reasons")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AbsenceReason {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long studentId;

    @Column(nullable = false)
    private String studentName;

    @Column(nullable = false)
    private String subjectName;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(nullable = false)
    private String source; // e.g. "AI_CALL", "MANUAL"

    @PrePersist
    protected void onCreate() {
        if (this.timestamp == null) {
            this.timestamp = LocalDateTime.now();
        }
    }
}
