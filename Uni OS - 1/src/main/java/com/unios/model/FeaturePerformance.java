package com.unios.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "feature_performance")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FeaturePerformance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String featureName; // "consistency", "variance", "marks", "entrance"

    @Column(nullable = false)
    private Double avgOutcomeScore;

    @Column(nullable = false)
    private Long batchId;
}
