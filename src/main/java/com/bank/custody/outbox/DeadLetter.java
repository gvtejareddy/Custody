package com.bank.custody.outbox;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "dead_letter")
public class DeadLetter {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sourceType;
    private String sourceId;

    @Column(columnDefinition = "text")
    private String payload;

    @Column(columnDefinition = "text")
    private String reason;

    private OffsetDateTime createdAt = OffsetDateTime.now();

    public Long getId() { return id; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
