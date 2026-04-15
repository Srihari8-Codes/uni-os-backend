package com.unios.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AgentActionOutcome — stores the result and effectiveness of an agent's action.
 *
 * This is the memory for the learning loop. It allows the system to track
 * which tools work best for which problems.
 */
@Entity
@Table(name = "agent_action_outcomes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentActionOutcome {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Name of the tool executed (e.g., PROMOTE_WAITLIST) */
    private String toolName;

    /** Status from ToolResult (SUCCESS, FAILED, PARTIAL, NO_ACTION_NEEDED) */
    private String status;

    /** 
     * Computed effectiveness score:
     * 1.0 = Success, 0.0 = Failure, 0.5 = Partial
     */
    private double effectivenessScore;

    /** 
     * Key representing the system state at the time (e.g., "STRESSED", "CRITICAL")
     * from UniversityStateBoard.
     */
    private String contextHealth;

    /** The agent that took the action */
    private String agentName;

    /** The primary reasoning for why this action was taken */
    @Column(length = 2000)
    private String reasoning;

    private LocalDateTime timestamp;

    /** Link to the original audit log if available */
    private String taskId;
}
