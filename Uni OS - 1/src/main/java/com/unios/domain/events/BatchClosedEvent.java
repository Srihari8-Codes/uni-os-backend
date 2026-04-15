package com.unios.domain.events;

import org.springframework.context.ApplicationEvent;

public class BatchClosedEvent extends ApplicationEvent {
    private final Long batchId;

    public BatchClosedEvent(Object source, Long batchId) {
        super(source);
        this.batchId = batchId;
    }

    public Long getBatchId() {
        return batchId;
    }
}
