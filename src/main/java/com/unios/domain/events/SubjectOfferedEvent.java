package com.unios.domain.events;

import lombok.Getter;

@Getter
public class SubjectOfferedEvent extends DomainEvent {
    private final Long offeringId;
    private final Long batchId;

    public SubjectOfferedEvent(Object source, Long offeringId, Long batchId) {
        super(source);
        this.offeringId = offeringId;
        this.batchId = batchId;
    }
}
