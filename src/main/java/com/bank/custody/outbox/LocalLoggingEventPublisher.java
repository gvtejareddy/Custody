package com.bank.custody.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "messaging", name = "mode", havingValue = "local", matchIfMissing = true)
public class LocalLoggingEventPublisher implements EventPublisher {
    private static final Logger log = LoggerFactory.getLogger(LocalLoggingEventPublisher.class);

    @Override
    public void publish(OutboxEvent event) {
        log.info("Published local outbox event id={} type={} aggregateType={} aggregateId={}",
                event.getId(), event.getType(), event.getAggregateType(), event.getAggregateId());
    }
}
