package com.bank.custody.api;

import com.bank.custody.wallet.WalletMapping;
import com.bank.custody.wallet.WalletMappingRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import java.util.List;

@RestController
@RequestMapping("/api/v1/wallets")
public class WalletController {
    private final WalletMappingRepository repo;

    public WalletController(WalletMappingRepository repo) {
        this.repo = repo;
    }

    @PostMapping
    public ResponseEntity<WalletMapping> createMapping(@RequestBody WalletMapping mapping) {
        WalletMapping saved = repo.save(mapping);
        return new ResponseEntity<>(saved, HttpStatus.CREATED);
    }

    @GetMapping("/by-account/{accountId}")
    public ResponseEntity<List<WalletMapping>> byAccount(@PathVariable Long accountId) {
        return ResponseEntity.ok(repo.findByAccountId(accountId));
    }
}
