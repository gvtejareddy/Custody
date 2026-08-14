package com.bank.custody.api;

import com.bank.custody.account.Account;
import com.bank.custody.account.AccountRepository;
import com.bank.custody.audit.AuditService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {
    private final AccountRepository accountRepository;
    private final AuditService auditService;

    public AccountController(AccountRepository accountRepository, AuditService auditService) {
        this.accountRepository = accountRepository;
        this.auditService = auditService;
    }

    @PostMapping
    public ResponseEntity<Account> createAccount(@RequestBody Account request) {
        // basic create — in real system, validate and check idempotency
        Account saved = accountRepository.save(request);
        auditService.record("ACCOUNT_CREATED", saved.getId() == null ? null : saved.getId().toString(), "customer=" + saved.getExternalCustomerId());
        return new ResponseEntity<>(saved, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Account> getAccount(@PathVariable Long id) {
        return accountRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
