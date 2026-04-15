package com.unios.domain.events;

import lombok.Getter;

@Getter
public class CreditsUpdatedEvent extends DomainEvent {
    private final Long studentId;
    private final int earnedCredits;

    public CreditsUpdatedEvent(Object source, Long studentId, int earnedCredits) {
        super(source);
        this.studentId = studentId;
        this.earnedCredits = earnedCredits;
    }
}
