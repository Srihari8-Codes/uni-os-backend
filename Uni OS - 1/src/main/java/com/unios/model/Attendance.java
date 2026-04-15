package com.unios.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDate;

@Entity
@Table(name = "attendance")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "slot_enrollment_id", nullable = false)
    private SlotEnrollment slotEnrollment;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private Boolean present;

    public boolean isPresent() {
        return Boolean.TRUE.equals(present);
    }
}
