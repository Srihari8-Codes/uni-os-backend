package com.unios.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "admission_weights")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdmissionWeights {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long batchId;

    @Column(nullable = false)
    private Double marksWeight = 0.4;

    @Column(nullable = false)
    private Double consistencyWeight = 0.2;

    @Column(nullable = false)
    private Double entranceWeight = 0.3;

    @Column(nullable = false)
    private Double variancePenalty = 0.1;

    @Column(columnDefinition = "TEXT")
    private String insights;

    @Column(columnDefinition = "TEXT")
    private String weightChanges;
}
