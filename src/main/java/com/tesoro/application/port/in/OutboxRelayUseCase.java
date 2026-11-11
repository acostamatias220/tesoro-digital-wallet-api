package com.tesoro.application.port.in;

public interface OutboxRelayUseCase {

    /** Publishes one batch of pending events and returns how many were published. */
    int publishPending();
}
