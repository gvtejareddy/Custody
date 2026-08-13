package com.bank.custody.outbox;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DeadLetterHandler {

    private final DeadLetterRepository deadLetterRepository;

    public DeadLetterHandler(DeadLetterRepository deadLetterRepository) {
        this.deadLetterRepository = deadLetterRepository;
    }

    @Transactional
    public void handle(OutboxEvent event, Exception failure) {
        try {
            DeadLetter dl = new DeadLetter();
            dl.setSourceType(event.getType());
            dl.setSourceId(event.getAggregateId() == null ? event.getId().toString() : event.getAggregateId().toString());
            dl.setPayload(event.getPayload());
            dl.setReason(failure == null ? "failed" : failure.getMessage());
            deadLetterRepository.save(dl);
        } catch (Exception e) {
            System.err.println("Failed to persist dead-letter: " + e.getMessage());
        }
    }
}
