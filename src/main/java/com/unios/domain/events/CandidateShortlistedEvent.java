package com.unios.domain.events;

import lombok.Getter;

@Getter
public class CandidateShortlistedEvent extends DomainEvent {
    private final Long candidateId;

    public CandidateShortlistedEvent(Object source, Long candidateId) {
        super(source);
        this.candidateId = candidateId;
    }
}
