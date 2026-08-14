package com.bank.custody.asset;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "asset")
public class Asset {
    @Id
    private String assetId; // e.g., BTC, ETH, USDC

    @Column(nullable = false)
    private String displayName;

    @Column(nullable = false)
    private String network; // e.g., bitcoin, ethereum

    @Column(nullable = false)
    private boolean enabled;

    public String getAssetId() { return assetId; }
    public void setAssetId(String assetId) { this.assetId = assetId; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getNetwork() { return network; }
    public void setNetwork(String network) { this.network = network; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
