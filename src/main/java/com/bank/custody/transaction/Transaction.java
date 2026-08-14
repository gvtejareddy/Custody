package com.bank.custody.transaction;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "custody_transaction")
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long accountId;

    @Column(nullable = false)
    private String assetId;

    @Column(nullable = false)
    private String direction; // IN/OUT

    @Column(nullable = false)
    private java.math.BigDecimal amount;

    private String externalProviderTxId; // fireblocks tx id

    @Column
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(unique = true)
    private String idempotencyKey;

    @Column
    private String status;

    // getters/setters omitted for brevity in scaffold
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public String getAssetId() { return assetId; }
    public void setAssetId(String assetId) { this.assetId = assetId; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public java.math.BigDecimal getAmount() { return amount; }
    public void setAmount(java.math.BigDecimal amount) { this.amount = amount; }
    public String getExternalProviderTxId() { return externalProviderTxId; }
    public void setExternalProviderTxId(String externalProviderTxId) { this.externalProviderTxId = externalProviderTxId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
