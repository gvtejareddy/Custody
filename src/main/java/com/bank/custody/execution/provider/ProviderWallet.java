package com.bank.custody.execution.provider;

public class ProviderWallet {
    private String providerVaultId;
    private String providerWalletId;
    private String depositAddress;

    public String getProviderVaultId() { return providerVaultId; }
    public void setProviderVaultId(String providerVaultId) { this.providerVaultId = providerVaultId; }

    public String getProviderWalletId() { return providerWalletId; }
    public void setProviderWalletId(String providerWalletId) { this.providerWalletId = providerWalletId; }

    public String getDepositAddress() { return depositAddress; }
    public void setDepositAddress(String depositAddress) { this.depositAddress = depositAddress; }
}
