package com.unios.domain.events;

import lombok.Getter;

@Getter
public class RiskDetectedEvent extends DomainEvent {
    private final Long studentId;
    private final String riskLevel;

    public RiskDetectedEvent(Object source, Long studentId, String riskLevel) {
        super(source);
        this.studentId = studentId;
        this.riskLevel = riskLevel;
    }
}
