package com.unios.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "batch_workflow_state")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private Long batchId;

    @Column(nullable = false)
    private String phase;

    @Column(nullable = false)
    private String lastEvent;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
