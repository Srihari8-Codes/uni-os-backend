package com.unios.domain.events;

import org.springframework.context.ApplicationEvent;

public class ApplicantPassedEvent extends ApplicationEvent {
    private final Long applicationId;

    public ApplicantPassedEvent(Object source, Long applicationId) {
        super(source);
        this.applicationId = applicationId;
    }

    public Long getApplicationId() {
        return applicationId;
    }
}
