package com.unios.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String recipient;
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String body;

    private String status; // SENT, FAILED
    private String errorMessage;
    private int retryCount;

    private LocalDateTime createdAt;
    private LocalDateTime sentAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = "PENDING";
    }
}
