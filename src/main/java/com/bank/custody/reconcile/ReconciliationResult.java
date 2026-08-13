package com.bank.custody.reconcile;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "reconciliation_result")
public class ReconciliationResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String assetId;
    private String network;
    private String status; // MATCH / BREAK / ERROR

    @Column(columnDefinition = "text")
    private String details;

    private OffsetDateTime createdAt = OffsetDateTime.now();

    public Long getId() { return id; }
    public String getAssetId() { return assetId; }
    public void setAssetId(String assetId) { this.assetId = assetId; }
    public String getNetwork() { return network; }
    public void setNetwork(String network) { this.network = network; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
