package com.unios.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "agent_experiences", indexes = {
    @Index(name = "idx_exp_goal", columnList = "goal_id"),
    @Index(name = "idx_exp_action", columnList = "action"),
    @Index(name = "idx_exp_score", columnList = "score")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentExperience {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "goal_id", nullable = false)
    private String goalId;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String result;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String outcome;

    @Column(nullable = false)
    private Double score;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;
}
