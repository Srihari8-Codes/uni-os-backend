package com.unios.domain.events;

import lombok.Getter;

@Getter
public class StartEnrollmentEvent extends DomainEvent {
    private final Long batchId;

    public StartEnrollmentEvent(Object source, Long batchId) {
        super(source);
        this.batchId = batchId;
    }
}
