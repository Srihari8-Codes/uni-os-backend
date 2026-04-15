package com.unios.domain.events;

import com.unios.model.Student;
import lombok.Getter;

@Getter
public class StudentEnrolledEvent extends DomainEvent {
    private final Student student;
    private final Long offeringId;

    public StudentEnrolledEvent(Object source, Student student, Long offeringId) {
        super(source);
        this.student = student;
        this.offeringId = offeringId;
    }
}
