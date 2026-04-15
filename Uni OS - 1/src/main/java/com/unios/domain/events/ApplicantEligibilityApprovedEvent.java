package com.unios.domain.events;

import org.springframework.context.ApplicationEvent;

public class ApplicantEligibilityApprovedEvent extends ApplicationEvent {
    private final Long applicationId;

    public ApplicantEligibilityApprovedEvent(Object source, Long applicationId) {
        super(source);
        this.applicationId = applicationId;
    }

    public Long getApplicationId() {
        return applicationId;
    }
}
