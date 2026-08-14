package com.bank.custody.api;

import com.bank.custody.execution.CustodyExecutionProvider;
import com.bank.custody.execution.provider.ProviderWallet;
import com.bank.custody.wallet.WalletMapping;
import com.bank.custody.wallet.WalletMappingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/custody-accounts")
public class DepositAddressController {
    private final CustodyExecutionProvider executionProvider;
    private final WalletMappingRepository mappingRepo;

    public DepositAddressController(CustodyExecutionProvider executionProvider, WalletMappingRepository mappingRepo) {
        this.executionProvider = executionProvider;
        this.mappingRepo = mappingRepo;
    }

    @PostMapping("/{accountId}/deposit-addresses")
    public ResponseEntity<Map<String,String>> createDepositAddress(@PathVariable Long accountId, @RequestBody Map<String,String> req) {
        String asset = req.get("asset");
        String network = req.getOrDefault("network", "");
        String idempotency = req.getOrDefault("idempotencyKey", UUID.randomUUID().toString());

        var existing = mappingRepo.findByAccountIdAndAssetId(accountId, asset);
        if (existing.isPresent() && existing.get().getBlockchainAddress() != null) {
            return ResponseEntity.ok(Map.of("address", existing.get().getBlockchainAddress()));
        }

        ProviderWallet pw = executionProvider.createOrGetWallet(accountId, asset, network, idempotency).join();

        WalletMapping mapping = mappingRepo.findByAccountIdAndAssetId(accountId, asset)
                .orElseGet(() -> {
                    WalletMapping m = new WalletMapping();
                    m.setId(UUID.randomUUID().toString());
                    m.setAccountId(accountId);
                    m.setAssetId(asset);
                    return m;
                });

        mapping.setProviderVaultId(pw.getProviderVaultId());
        mapping.setProviderWalletId(pw.getProviderWalletId());
        mapping.setBlockchainAddress(pw.getDepositAddress());
        mappingRepo.save(mapping);

        return new ResponseEntity<>(Map.of("address", pw.getDepositAddress() == null ? "" : pw.getDepositAddress()), HttpStatus.CREATED);
    }
}
