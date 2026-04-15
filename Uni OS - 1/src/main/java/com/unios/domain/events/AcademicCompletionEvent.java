package com.unios.domain.events;

import lombok.Getter;

@Getter
public class AcademicCompletionEvent extends DomainEvent {
    private final Long slotEnrollmentId;
    private final int marks;

    public AcademicCompletionEvent(Object source, Long slotEnrollmentId, int marks) {
        super(source);
        this.slotEnrollmentId = slotEnrollmentId;
        this.marks = marks;
    }
}
