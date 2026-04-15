package com.unios.domain.events;

import lombok.Getter;

@Getter
public class GraduationCandidateEvent extends DomainEvent {
    private final Long studentId;

    public GraduationCandidateEvent(Object source, Long studentId) {
        super(source);
        this.studentId = studentId;
    }
}
