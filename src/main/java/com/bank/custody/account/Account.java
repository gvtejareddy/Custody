package com.bank.custody.account;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "custody_account")
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String externalCustomerId; // bank customer reference

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    // getters / setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getExternalCustomerId() { return externalCustomerId; }
    public void setExternalCustomerId(String externalCustomerId) { this.externalCustomerId = externalCustomerId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
