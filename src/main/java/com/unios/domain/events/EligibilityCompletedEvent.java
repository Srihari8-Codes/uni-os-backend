package com.unios.domain.events;

public class EligibilityCompletedEvent extends DomainEvent {
    private final Long batchId;

    public EligibilityCompletedEvent(Object source, Long batchId) {
        super(source);
        this.batchId = batchId;
    }

    public Long getBatchId() {
        return batchId;
    }
}
