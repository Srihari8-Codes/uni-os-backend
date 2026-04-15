package com.unios.domain.events;

public class ExamScheduledEvent extends DomainEvent {
    private final Long batchId;

    public ExamScheduledEvent(Object source, Long batchId) {
        super(source);
        this.batchId = batchId;
    }

    public Long getBatchId() {
        return batchId;
    }
}
