package com.bank.custody.outbox;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "messaging", name = "mode", havingValue = "servicebus-emulator")
public class ServiceBusPublisher implements EventPublisher {

    private final String connectionString;
    private final String queueName;
    private ServiceBusSenderClient sender;

    public ServiceBusPublisher(@Value("${messaging.servicebus.connection-string:}") String connectionString,
                               @Value("${outbox.queue-name:outbox-events}") String queueName) {
        this.connectionString = connectionString;
        this.queueName = queueName;
    }

    @PostConstruct
    public void init() {
        if (connectionString == null || connectionString.isBlank()) return;
        this.sender = new ServiceBusClientBuilder()
                .connectionString(connectionString)
                .sender()
                .queueName(queueName)
                .buildClient();
    }

    @Override
    public void publish(OutboxEvent e) {
        if (sender == null) throw new IllegalStateException("ServiceBus sender not initialized");
        ServiceBusMessage msg = new ServiceBusMessage(e.getPayload());
        msg.setContentType("application/json");
        msg.setMessageId("outbox-" + e.getId());
        msg.getApplicationProperties().put("aggregateType", e.getAggregateType());
        msg.getApplicationProperties().put("aggregateId", e.getAggregateId());
        msg.getApplicationProperties().put("type", e.getType());
        sender.sendMessage(msg);
    }

    @PreDestroy
    public void shutdown() {
        if (sender != null) sender.close();
    }
}
