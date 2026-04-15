package com.unios.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * DB Schema representation for stored Agent Strategies.
 * CREATE TABLE agent_strategies (
 *     id BIGSERIAL PRIMARY KEY,
 *     goal_context VARCHAR(100) NOT NULL,
 *     strategy TEXT NOT NULL,
 *     confidence_score DOUBLE PRECISION NOT NULL,
 *     usage_count INTEGER DEFAULT 0,
 *     created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
 *     updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
 * );
 * CREATE INDEX idx_strategy_goal ON agent_strategies(goal_context);
 * CREATE INDEX idx_strategy_score ON agent_strategies(confidence_score);
 */
@Entity
@Table(name = "agent_strategies", indexes = {
    @Index(name = "idx_strategy_goal", columnList = "goal_context"),
    @Index(name = "idx_strategy_score", columnList = "confidence_score")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentStrategy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "goal_context", nullable = false, length = 100)
    private String goalContext;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String strategy;

    @Column(name = "confidence_score", nullable = false)
    private Double confidenceScore;

    @Column(name = "usage_count", nullable = false)
    @Builder.Default
    private Integer usageCount = 0;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
