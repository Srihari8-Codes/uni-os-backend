package com.unios.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "strategic_lessons")
@Getter
@Setter
public class StrategicLesson {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long sourceBatchId;
    
    @Column(columnDefinition = "TEXT")
    private String lesson;

    private LocalDateTime createdAt;
}
