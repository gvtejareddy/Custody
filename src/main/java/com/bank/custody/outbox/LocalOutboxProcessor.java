package com.bank.custody.outbox;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Component
public class LocalOutboxProcessor {

    private final OutboxRepository outboxRepository;
    private final ProviderEventProcessor providerEventProcessor;
    private final DeadLetterHandler deadLetterHandler;

    public LocalOutboxProcessor(OutboxRepository outboxRepository, ProviderEventProcessor providerEventProcessor, DeadLetterHandler deadLetterHandler) {
        this.outboxRepository = outboxRepository;
        this.providerEventProcessor = providerEventProcessor;
        this.deadLetterHandler = deadLetterHandler;
    }

    @Scheduled(fixedDelayString = "${outbox.local-poll-ms:3000}")
    @Transactional
    public void processPending() {
        List<OutboxEvent> pending = outboxRepository.findPending(OffsetDateTime.now());
        for (OutboxEvent e : pending) {
            if ("provider_event".equalsIgnoreCase(e.getAggregateType())) {
                try {
                    providerEventProcessor.process(e.getAggregateId(), e.getPayload());
                    e.setProcessed(true);
                    outboxRepository.save(e);
                } catch (Exception ex) {
                    e.setAttempts(e.getAttempts() + 1);
                    e.setNextAttemptAt(OffsetDateTime.now().plusSeconds(30 * e.getAttempts()));
                    outboxRepository.save(e);
                    if (e.getAttempts() > 5) {
                        deadLetterHandler.handle(e, ex);
                        e.setProcessed(true);
                        outboxRepository.save(e);
                    }
                }
            }
        }
    }
}
