package com.unios.domain.events;

import lombok.Getter;

@Getter
public class EnrollmentCompletedEvent extends DomainEvent {
    private final Long batchId;

    public EnrollmentCompletedEvent(Object source, Long batchId) {
        super(source);
        this.batchId = batchId;
    }
}
