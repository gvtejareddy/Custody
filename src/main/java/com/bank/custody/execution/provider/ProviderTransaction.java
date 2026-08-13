package com.bank.custody.execution.provider;

public class ProviderTransaction {
    private String providerTransactionId;
    private String status;

    public String getProviderTransactionId() { return providerTransactionId; }
    public void setProviderTransactionId(String providerTransactionId) { this.providerTransactionId = providerTransactionId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
