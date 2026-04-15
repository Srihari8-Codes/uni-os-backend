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
public class FailedTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String taskId;
    private String agentName;
    private String entityType;
    private Long entityId;

    @Column(length = 4096)
    private String errorMessage;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String stackTrace;

    private LocalDateTime failedAt;

    @PrePersist
    protected void onFail() {
        failedAt = LocalDateTime.now();
    }
}
