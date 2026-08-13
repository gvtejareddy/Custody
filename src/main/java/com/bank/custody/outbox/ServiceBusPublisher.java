package com.bank.custody.outbox;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.azure.messaging.servicebus.ServiceBusMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Component
public class ServiceBusPublisher {

    private final String connectionString;
    private final String queueName;
    private ServiceBusSenderClient sender;

    public ServiceBusPublisher(@Value("${AZURE_SERVICE_BUS_CONNECTION_STRING:}") String connectionString,
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

    public void send(OutboxEvent e) {
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
