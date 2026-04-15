package com.unios.domain.events;

import lombok.Getter;

@Getter
public class StartEligibilityCheckEvent extends DomainEvent {
    private final Long batchId;

    public StartEligibilityCheckEvent(Object source, Long batchId) {
        super(source);
        this.batchId = batchId;
    }
}
