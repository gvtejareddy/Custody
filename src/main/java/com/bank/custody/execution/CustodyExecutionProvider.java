package com.bank.custody.execution;

import com.bank.custody.execution.provider.ProviderTransaction;
import com.bank.custody.execution.provider.ProviderWallet;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

public interface CustodyExecutionProvider {
    CompletableFuture<ProviderWallet> createOrGetWallet(Long custodyAccountId, String asset, String network, String idempotencyKey);

    CompletableFuture<ProviderTransaction> submitWithdrawal(Long custodyTransactionId,
                                                             Long custodyAccountId,
                                                             String asset,
                                                             String network,
                                                             BigDecimal amount,
                                                             String destinationAddress,
                                                             String idempotencyKey);
}
