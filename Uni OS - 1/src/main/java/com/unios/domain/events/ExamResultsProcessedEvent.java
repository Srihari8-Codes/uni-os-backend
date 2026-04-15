package com.unios.domain.events;

import lombok.Getter;

@Getter
public class ExamResultsProcessedEvent extends DomainEvent {
    private final Long batchId;

    public ExamResultsProcessedEvent(Object source, Long batchId) {
        super(source);
        this.batchId = batchId;
    }
}
