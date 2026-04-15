package com.unios.domain.events;

import org.springframework.context.ApplicationEvent;

public class ProceedToExamEvent extends ApplicationEvent {
    private final Long offeringId;

    public ProceedToExamEvent(Object source, Long offeringId) {
        super(source);
        this.offeringId = offeringId;
    }

    public Long getOfferingId() {
        return offeringId;
    }
}
