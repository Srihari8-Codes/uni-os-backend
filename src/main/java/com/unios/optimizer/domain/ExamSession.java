package com.unios.optimizer.domain;

import org.optaplanner.core.api.domain.entity.PlanningEntity;
import org.optaplanner.core.api.domain.lookup.PlanningId;
import org.optaplanner.core.api.domain.variable.PlanningVariable;
import org.optaplanner.core.api.domain.entity.PlanningPin;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalTime;

@PlanningEntity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExamSession {

    @PlanningId
    private Long id;
    private Long applicationId;

    // We might need studentId to check for conflicts if multiple exams happen same
    // day
    private Long studentId;

    // Planning variables: Room and Seat? Or just Room?
    // User requirement: "No room overflow", "No collisions", "Seats balanced"
    // "ExamSession (planning entity)... assignedRoom, seatNumber, timeSlot"

    // For simplicity, we'll try to assign a Room and a TimeSlot.
    // Seat number can be derived or just checked against capacity.
    // However, if we want "seatNumber" optimization, we might need it.
    // Let's stick to Room and TimeSlot first, and maybe just ensure count <
    // capacity.
    // But user asked for "seatNumber" in the entity fields in the prompt.
    // Let's map it.

    @PlanningVariable(valueRangeProviderRefs = "roomRange")
    private Room assignedRoom;

    // We can treat timeSlot as a variable if exams can be moved.
    @PlanningVariable(valueRangeProviderRefs = "timeSlotRange")
    private LocalTime timeSlot;

    // Seat number is tricky as a variable unless we have a range of 1..MaxCapacity.
    // If we just ensure capacity isn't exceeded, actual seat number assignment can
    // be post-processing
    // OR we can make it a shadow variable?
    // OptaPlanner usually handles "bin packing" (exams into rooms).
    // Let's keep it simple: We optimize Room and Time.
    // Seat allocation can be done simply by index in the room list later.
    // But the prompt explicitly listed "seatNumber" as a field.
    // I will include it as a field to be populated, but maybe not a planning
    // variable involved in the core constraints
    // unless we need specific seat spacing constraints (which wasn't asked, just
    // "balanced").
    // Actually, "Seats balanced" might mean spreading students across rooms?

    private String subject; // Added back

    @PlanningPin
    private boolean pinned = false;

    public ExamSession(Long id, Long applicationId, Long studentId, String subject) {
        this.id = id;
        this.applicationId = applicationId;
        this.studentId = studentId;
        this.subject = subject;
    }
    
    public ExamSession(Long id, Long applicationId, Long studentId, String subject, Room room, LocalTime time, boolean pinned) {
        this(id, applicationId, studentId, subject);
        this.assignedRoom = room;
        this.timeSlot = time;
        this.pinned = pinned;
    }
}
