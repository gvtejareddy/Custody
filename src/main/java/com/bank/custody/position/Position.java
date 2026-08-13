package com.bank.custody.position;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "position", uniqueConstraints = {@UniqueConstraint(columnNames = {"account_id","asset_id"})})
public class Position {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "asset_id", nullable = false)
    private String assetId;

    @Column(nullable = false)
    private BigDecimal available = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal locked = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal pending = BigDecimal.ZERO;

    @Column(nullable = false)
    private Long version = 0L;

    @Column(name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public Long getId() { return id; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public String getAssetId() { return assetId; }
    public void setAssetId(String assetId) { this.assetId = assetId; }
    public BigDecimal getAvailable() { return available; }
    public void setAvailable(BigDecimal available) { this.available = available; }
    public BigDecimal getLocked() { return locked; }
    public void setLocked(BigDecimal locked) { this.locked = locked; }
    public BigDecimal getPending() { return pending; }
    public void setPending(BigDecimal pending) { this.pending = pending; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
