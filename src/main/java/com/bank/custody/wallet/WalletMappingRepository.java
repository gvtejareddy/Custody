package com.bank.custody.wallet;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WalletMappingRepository extends JpaRepository<WalletMapping, String> {
    List<WalletMapping> findByAccountId(Long accountId);
    java.util.Optional<WalletMapping> findByAccountIdAndAssetId(Long accountId, String assetId);
    java.util.Optional<WalletMapping> findByBlockchainAddress(String address);
    java.util.Optional<WalletMapping> findByProviderVaultId(String vaultId);
}
