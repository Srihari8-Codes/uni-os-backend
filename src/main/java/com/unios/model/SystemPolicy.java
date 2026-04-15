package com.unios.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "system_policies")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SystemPolicy {

    @Id
    @Column(name = "policy_key", nullable = false, unique = true)
    private String policyKey;

    @Column(name = "policy_value", nullable = false)
    private Double policyValue;

    @Column(length = 1000)
    private String description;

    private LocalDateTime lastUpdated;

    @PreUpdate
    @PrePersist
    public void updateTimestamp() {
        this.lastUpdated = LocalDateTime.now();
    }
}
