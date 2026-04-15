package com.unios.domain.events;

import lombok.Getter;

@Getter
public class JobOpeningCreatedEvent extends DomainEvent {
    private final Long candidateId; // Linking the application/candidate to the opening logic
    private final String department;

    // This event simulates an "Opening" matching a candidate application
    public JobOpeningCreatedEvent(Object source, Long candidateId, String department) {
        super(source);
        this.candidateId = candidateId;
        this.department = department;
    }
}
