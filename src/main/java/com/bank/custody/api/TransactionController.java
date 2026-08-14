package com.bank.custody.api;

import com.bank.custody.transaction.Transaction;
import com.bank.custody.transaction.TransactionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {
    private final TransactionRepository txRepo;

    public TransactionController(TransactionRepository txRepo) {
        this.txRepo = txRepo;
    }

    @PostMapping
    public ResponseEntity<Transaction> create(@RequestBody Transaction tx) {
        Transaction saved = txRepo.save(tx);
        return new ResponseEntity<>(saved, HttpStatus.CREATED);
    }

    @GetMapping("/by-account/{accountId}")
    public ResponseEntity<List<Transaction>> byAccount(@PathVariable Long accountId) {
        return ResponseEntity.ok(txRepo.findByAccountId(accountId));
    }
}
