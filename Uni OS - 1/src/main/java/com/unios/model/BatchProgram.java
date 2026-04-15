package com.unios.model;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "batch_programs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchProgram {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "batch_id", nullable = false)
    @JsonIgnore
    private Batch batch;

    @ManyToOne
    @JoinColumn(name = "program_id", nullable = false)
    private Program program;

    /** JSON array: [{name,credits,prerequisites:[],mandatory:bool},...] */
    @Column(columnDefinition = "TEXT")
    private String subjects;

    private Integer totalCreditsRequired;
}
