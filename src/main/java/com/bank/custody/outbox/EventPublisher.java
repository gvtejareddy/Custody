package com.bank.custody.outbox;

/** Publishes an outbox event without coupling domain code to a message broker. */
public interface EventPublisher {
    void publish(OutboxEvent event);
}
