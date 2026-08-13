package com.bank.custody.api;

import com.bank.custody.transaction.TransactionOrchestrator;
import com.bank.custody.transaction.Transaction;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/custody-accounts")
public class WithdrawalController {
    private final TransactionOrchestrator orchestrator;

    public WithdrawalController(TransactionOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/{accountId}/withdrawals")
    public ResponseEntity<?> requestWithdrawal(@PathVariable Long accountId,
                                               @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                               @RequestBody Map<String,String> req) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error","Missing Idempotency-Key header"));
        }

        String asset = req.get("asset");
        String network = req.getOrDefault("network", "");
        String amount = req.get("amount");
        String destination = req.get("destinationAddress");

        try {
            java.math.BigDecimal amt = new java.math.BigDecimal(amount);
            Transaction tx = orchestrator.requestWithdrawal(accountId, asset, network, amt, destination, idempotencyKey);
            return ResponseEntity.status(HttpStatus.CREATED).body(tx);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }
}
