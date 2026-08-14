package com.bank.custody.outbox;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Component
public class OutboxPublisher {

    private final OutboxRepository outboxRepository;
    private final EventPublisher eventPublisher;
    private final DeadLetterHandler deadLetterHandler;

    public OutboxPublisher(OutboxRepository outboxRepository, EventPublisher eventPublisher, DeadLetterHandler deadLetterHandler) {
        this.outboxRepository = outboxRepository;
        this.eventPublisher = eventPublisher;
        this.deadLetterHandler = deadLetterHandler;
    }

    @Scheduled(fixedDelayString = "${outbox.poll-ms:5000}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> pending = outboxRepository.findPending(OffsetDateTime.now());
        for (OutboxEvent e : pending) {
            try {
                eventPublisher.publish(e);
                e.setProcessed(true);
                outboxRepository.save(e);
                } catch (Exception ex) {
                    e.setAttempts(e.getAttempts() + 1);
                    e.setNextAttemptAt(OffsetDateTime.now().plusSeconds(30 * e.getAttempts()));
                    outboxRepository.save(e);
                    if (e.getAttempts() > 5) {
                        try {
                            deadLetterHandler.handle(e, ex);
                            e.setProcessed(true);
                            outboxRepository.save(e);
                        } catch (Exception inner) {
                            System.err.println("Failed to move outbox to dead-letter: " + inner.getMessage());
                        }
                    }
                }
        }
    }
}
