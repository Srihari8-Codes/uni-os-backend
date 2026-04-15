package com.unios.domain.events;

import com.unios.model.Student;
import lombok.Getter;

@Getter
public class CreditsCompletedEvent extends DomainEvent {
    private final Student student;
    private final int totalCredits;

    public CreditsCompletedEvent(Object source, Student student, int totalCredits) {
        super(source);
        this.student = student;
        this.totalCredits = totalCredits;
    }
}
