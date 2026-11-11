package com.tesoro.adapter.out.messaging;

import com.tesoro.application.model.OutboxMessage;
import com.tesoro.application.port.out.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Default publisher: writes each event as a structured log line. Swapping it for a Kafka or
 * RabbitMQ adapter only requires another {@link EventPublisher} implementation.
 */
@Component
class LoggingEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger("tesoro.events");

    @Override
    public void publish(OutboxMessage message) {
        log.info(
                "event={} id={} aggregate={}:{} payload={}",
                message.eventType(),
                message.id(),
                message.aggregateType(),
                message.aggregateId(),
                message.payload());
    }
}
