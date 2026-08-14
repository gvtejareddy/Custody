package com.bank.custody.transaction;

import com.bank.custody.compliance.ComplianceProvider;
import com.bank.custody.execution.CustodyExecutionProvider;
import com.bank.custody.execution.provider.ProviderTransaction;
import com.bank.custody.ledger.LedgerService;
import com.bank.custody.policy.PolicyEngine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class TransactionOrchestrator {
    private final TransactionRepository txRepo;
    private final LedgerService ledgerService;
    private final CustodyExecutionProvider executionProvider;
    private final ComplianceProvider complianceProvider;
    private final PolicyEngine policyEngine;
    private final com.bank.custody.audit.AuditService auditService;

    public TransactionOrchestrator(TransactionRepository txRepo, LedgerService ledgerService, CustodyExecutionProvider executionProvider, ComplianceProvider complianceProvider, PolicyEngine policyEngine, com.bank.custody.audit.AuditService auditService) {
        this.txRepo = txRepo;
        this.ledgerService = ledgerService;
        this.executionProvider = executionProvider;
        this.complianceProvider = complianceProvider;
        this.policyEngine = policyEngine;
        this.auditService = auditService;
    }

    @Transactional
    public Transaction requestWithdrawal(Long accountId, String assetId, String network, BigDecimal amount, String destinationAddress, String idempotencyKey) {
        // idempotency
        var existing = txRepo.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) return existing.get();

        BigDecimal available = ledgerService.calculateAvailable(accountId, assetId);
        // AML screening
        var screening = complianceProvider.screenWithdrawal(accountId, assetId, amount, destinationAddress);
        if (!screening.passed) {
            throw new IllegalStateException("AML screening failed: " + screening.reason);
        }

        // Policy
        Transaction txCandidate = new Transaction();
        txCandidate.setAccountId(accountId);
        txCandidate.setAssetId(assetId);
        txCandidate.setAmount(amount);
        var policyDecision = policyEngine.evaluate(txCandidate);
        if (!"ALLOW".equalsIgnoreCase(policyDecision.decision)) {
            throw new IllegalStateException("Policy denied");
        }
        if (available.compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient funds");
        }

        // reserve funds by creating a DEBIT ledger entry
        ledgerService.reserveDebit(accountId, assetId, amount);

        Transaction tx = new Transaction();
        tx.setAccountId(accountId);
        tx.setAssetId(assetId);
        tx.setDirection("OUT");
        tx.setAmount(amount);
        tx.setIdempotencyKey(idempotencyKey);
        tx.setStatus("FUNDS_RESERVED");
        tx = txRepo.save(tx);

        // submit to provider (blocking for MVP)
        auditService.record("WithdrawalRequested", tx.getId() == null ? null : tx.getId().toString(), "amount=" + amount.toPlainString() + " dest=" + destinationAddress);
        ProviderTransaction pt = executionProvider.submitWithdrawal(tx.getId(), accountId, assetId, network, amount, destinationAddress, idempotencyKey).join();
        tx.setExternalProviderTxId(pt.getProviderTransactionId());
        tx.setStatus(pt.getStatus());
        auditService.record("WithdrawalSubmitted", tx.getId() == null ? null : tx.getId().toString(), "providerTx=" + pt.getProviderTransactionId());
        return txRepo.save(tx);
    }
}
