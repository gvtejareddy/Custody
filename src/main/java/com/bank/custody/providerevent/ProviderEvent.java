package com.bank.custody.providerevent;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "provider_event", indexes = {
        @Index(name = "idx_provider_event_provider_event_id", columnList = "provider, provider_event_id", unique = true)
})
public class ProviderEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String provider;

    @Column(name = "provider_event_id")
    private String providerEventId;

    @Column(name = "event_type")
    private String eventType;

    @Column(columnDefinition = "jsonb")
    private String payload;

    @Column(name = "received_at")
    private OffsetDateTime receivedAt = OffsetDateTime.now();

    private boolean processed = false;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    public ProviderEvent() {}

    public ProviderEvent(String provider, String providerEventId, String eventType, String payload) {
        this.provider = provider;
        this.providerEventId = providerEventId;
        this.eventType = eventType;
        this.payload = payload;
        this.receivedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getProviderEventId() { return providerEventId; }
    public void setProviderEventId(String providerEventId) { this.providerEventId = providerEventId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public OffsetDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(OffsetDateTime receivedAt) { this.receivedAt = receivedAt; }

    public boolean isProcessed() { return processed; }
    public void setProcessed(boolean processed) { this.processed = processed; }
    public OffsetDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(OffsetDateTime processedAt) { this.processedAt = processedAt; }
}
