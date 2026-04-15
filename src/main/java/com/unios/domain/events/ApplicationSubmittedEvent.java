package com.unios.domain.events;

import com.unios.model.Application;
import lombok.Getter;

@Getter
public class ApplicationSubmittedEvent extends DomainEvent {
    private final Application application;

    public ApplicationSubmittedEvent(Object source, Application application) {
        super(source);
        this.application = application;
    }
}
