package com.unios.domain.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;

@Getter
public abstract class DomainEvent extends ApplicationEvent {
    private final LocalDateTime occurredAt;

    public DomainEvent(Object source) {
        super(source);
        this.occurredAt = LocalDateTime.now();
    }
}
