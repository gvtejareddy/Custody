package com.bank.custody.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LedgerRepository extends JpaRepository<LedgerEntry, Long> {
    List<LedgerEntry> findByAccountId(Long accountId);
}
