package com.bank.custody.outbox;

import com.bank.custody.ledger.LedgerService;
import com.bank.custody.providerevent.ProviderEvent;
import com.bank.custody.providerevent.ProviderEventRepository;
import com.bank.custody.transaction.Transaction;
import com.bank.custody.transaction.TransactionRepository;
import com.bank.custody.wallet.WalletMapping;
import com.bank.custody.wallet.WalletMappingRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

@Component
public class ProviderEventProcessor {
    private final ProviderEventRepository providerEventRepository;
    private final WalletMappingRepository walletMappingRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerService ledgerService;
    private final com.bank.custody.audit.AuditService auditService;
    private final ObjectMapper mapper = new ObjectMapper();

    public ProviderEventProcessor(ProviderEventRepository providerEventRepository,
                                  WalletMappingRepository walletMappingRepository,
                                  TransactionRepository transactionRepository,
                                  LedgerService ledgerService,
                                  com.bank.custody.audit.AuditService auditService) {
        this.providerEventRepository = providerEventRepository;
        this.walletMappingRepository = walletMappingRepository;
        this.transactionRepository = transactionRepository;
        this.ledgerService = ledgerService;
        this.auditService = auditService;
    }

    public void process(String providerEventId, String payloadJson) throws Exception {
        // idempotent: if providerEvent already processed (presence of provider_event record), skip
        Optional<ProviderEvent> evOpt = providerEventRepository.findByProviderAndProviderEventId("fireblocks", providerEventId);
        if (evOpt.isEmpty()) return;
        ProviderEvent provEvent = evOpt.get();
        if (provEvent.isProcessed()) return;

        // parse payload and attempt to find an address or vault id
        Map<String,Object> payload = mapper.readValue(payloadJson, new TypeReference<Map<String,Object>>(){});
        String addr = extractAddress(payload);
        String vaultId = extractVaultId(payload);

        // find mapping by address or vault
        java.util.Optional<WalletMapping> mappingOpt = addr != null ? walletMappingRepository.findByBlockchainAddress(addr) : java.util.Optional.<WalletMapping>empty();
        if (mappingOpt.isEmpty() && vaultId != null) mappingOpt = walletMappingRepository.findByProviderVaultId(vaultId);
        if (mappingOpt.isEmpty()) {
            // unknown address: leave for manual investigation
            return;
        }

        var mapping = mappingOpt.get();
        Long accountId = mapping.getAccountId();
        String assetId = mapping.getAssetId();

        // amount extraction
        BigDecimal amount = extractAmount(payload);
        if (amount == null) return; // nothing to credit

        // create custody transaction
        Transaction tx = new Transaction();
        tx.setAccountId(accountId);
        tx.setAssetId(assetId);
        tx.setDirection("IN");
        tx.setAmount(amount);

        // decide whether to post immediately based on provider confirmation
        boolean confirmed = isConfirmed(payload);
        if (confirmed) {
            tx.setStatus("POSTED");
            transactionRepository.save(tx);
            ledgerService.credit(accountId, assetId, amount);
            auditService.record("DepositPosted", tx.getId() == null ? null : tx.getId().toString(), "providerEvent=" + providerEventId + " amount=" + amount.toPlainString());
        } else {
            tx.setStatus("PENDING_CONFIRMATION");
            transactionRepository.save(tx);
            auditService.record("DepositPending", tx.getId() == null ? null : tx.getId().toString(), "providerEvent=" + providerEventId + " amount=" + amount.toPlainString());
        }

        // mark provider event processed
        provEvent.setProcessed(true);
        provEvent.setProcessedAt(java.time.OffsetDateTime.now());
        providerEventRepository.save(provEvent);
    }

    private boolean isConfirmed(Map<String,Object> payload) {
        Object status = payload.get("status");
        if (status != null) {
            String s = status.toString().toLowerCase();
            if (s.contains("confirmed") || s.contains("completed") || s.contains("settled")) return true;
        }
        Object conf = payload.get("confirmations");
        if (conf != null) {
            try {
                int c = Integer.parseInt(conf.toString());
                return c > 0;
            } catch (Exception ignored) {}
        }
        return false;
    }

    private String extractAddress(Map<String,Object> payload) {
        Object o;
        if ((o = payload.get("address")) != null) return o.toString();
        if ((o = payload.get("destination")) instanceof Map) {
            Object a = ((Map<?,?>)o).get("address");
            if (a != null) return a.toString();
        }
        if ((o = payload.get("toAddress")) != null) return o.toString();
        // nested checks
        if ((o = payload.get("transaction")) instanceof Map) {
            Object a = ((Map<?,?>)o).get("toAddress");
            if (a != null) return a.toString();
        }
        return null;
    }

    private String extractVaultId(Map<String,Object> payload) {
        Object o;
        if ((o = payload.get("vaultAccountId")) != null) return o.toString();
        if ((o = payload.get("vaultId")) != null) return o.toString();
        return null;
    }

    private BigDecimal extractAmount(Map<String,Object> payload) {
        Object o;
        if ((o = payload.get("amount")) != null) return new BigDecimal(o.toString());
        if ((o = payload.get("value")) != null) return new BigDecimal(o.toString());
        if ((o = payload.get("amountUsd")) != null) return new BigDecimal(o.toString());
        if ((o = payload.get("transaction")) instanceof Map) {
            Object a = ((Map<?,?>)o).get("amount");
            if (a != null) return new BigDecimal(a.toString());
        }
        return null;
    }
}
