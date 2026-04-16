package com.unios.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Map;

@Entity
@Table(name = "university_goals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Goal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private GoalType type;

    @Column(name = "category")
    private String category; // Compatibility for String-based lookups

    @Column(columnDefinition = "TEXT", length = 2048)
    private String description;

    @Column(name = "goal_statement", length = 2048)
    private String goalStatement;

    @Column(name = "agent_name")
    private String agentName;

    @Builder.Default
    private int priority = 50;

    @Builder.Default
    private double urgencyScore = 0.5;

    @Column(name = "depends_on_goal_ids", length = 512)
    private String dependsOnGoalIds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> constraints;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "success_criteria", columnDefinition = "jsonb")
    private Map<String, Object> successCriteria;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private GoalStatus status = GoalStatus.ACTIVE;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "progress_metrics", columnDefinition = "jsonb")
    private Map<String, Object> progressMetrics;

    @Column(name = "abandon_reason", length = 1024)
    private String abandonReason;

    @Column(name = "deadline")
    private ZonedDateTime deadline;

    @Column(name = "last_pursued_at")
    private LocalDateTime lastPursuedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    public enum GoalType {
        ADMISSIONS,
        ATTENDANCE,
        FINANCE,
        ACADEMICS,
        HR
    }

    public enum GoalStatus {
        ACTIVE,
        PAUSED,
        ABANDONED,
        COMPLETED,
        PARTIALLY_COMPLETED,
        FAILED,
        TIMEOUT
    }
}
