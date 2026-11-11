package com.tesoro.application.port.out;

import com.tesoro.application.model.OutboxMessage;

/** Delivers outbox messages to the outside world (a log, a message broker, a webhook...). */
public interface EventPublisher {

    void publish(OutboxMessage message);
}
