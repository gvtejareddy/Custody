package com.bank.custody.wallet;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;

@Entity
@Table(name = "wallet_mapping")
public class WalletMapping {
    @Id
    private String id; // mapping id (could be vault account id)

    @Column(nullable = false)
    private Long accountId; // bank internal account

    @Column(nullable = false)
    private String assetId;

    @Column(nullable = false)
    private String providerVaultId; // Fireblocks vault account id

    @Column
    private String providerWalletId; // optional provider wallet id (per-asset)

    @Column
    private String blockchainAddress; // optional deposit address

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }

    public String getAssetId() { return assetId; }
    public void setAssetId(String assetId) { this.assetId = assetId; }

    public String getProviderVaultId() { return providerVaultId; }
    public void setProviderVaultId(String providerVaultId) { this.providerVaultId = providerVaultId; }

    public String getProviderWalletId() { return providerWalletId; }
    public void setProviderWalletId(String providerWalletId) { this.providerWalletId = providerWalletId; }

    public String getBlockchainAddress() { return blockchainAddress; }
    public void setBlockchainAddress(String blockchainAddress) { this.blockchainAddress = blockchainAddress; }
}
