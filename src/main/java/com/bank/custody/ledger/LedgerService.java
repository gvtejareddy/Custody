package com.bank.custody.ledger;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import com.bank.custody.position.PositionRepository;
import com.bank.custody.position.Position;
import java.time.OffsetDateTime;

@Service
public class LedgerService {
    private final LedgerRepository ledgerRepo;
    private final PositionRepository positionRepository;
    private final com.bank.custody.audit.AuditService auditService;

    public LedgerService(LedgerRepository ledgerRepo) {
        this(ledgerRepo, null, null);
    }

    public LedgerService(LedgerRepository ledgerRepo, PositionRepository positionRepository) {
        this(ledgerRepo, positionRepository, null);
    }

    @Autowired
    public LedgerService(LedgerRepository ledgerRepo, PositionRepository positionRepository, com.bank.custody.audit.AuditService auditService) {
        this.ledgerRepo = ledgerRepo;
        this.positionRepository = positionRepository;
        this.auditService = auditService;
    }

    @Transactional
    public LedgerEntry credit(Long accountId, String assetId, java.math.BigDecimal amount) {
        // Credit increases available
        Position pos = positionRepository.findByAccountIdAndAssetIdForUpdate(accountId, assetId)
                .orElseGet(() -> {
                    Position p = new Position();
                    p.setAccountId(accountId);
                    p.setAssetId(assetId);
                    p.setAvailable(java.math.BigDecimal.ZERO);
                    p.setLocked(java.math.BigDecimal.ZERO);
                    return positionRepository.save(p);
                });

        pos.setAvailable(pos.getAvailable().add(amount));
        pos.setUpdatedAt(java.time.OffsetDateTime.now());
        positionRepository.save(pos);

        LedgerEntry e = new LedgerEntry();
        e.setAccountId(accountId);
        e.setAssetId(assetId);
        e.setAmount(amount);
        e.setType("CREDIT");
        return ledgerRepo.save(e);
    }

    public BigDecimal calculateAvailable(Long accountId, String assetId) {
        List<LedgerEntry> entries = ledgerRepo.findByAccountId(accountId);
        return entries.stream()
                .filter(e -> assetId.equals(e.getAssetId()))
                .map(e -> e.getType().equalsIgnoreCase("CREDIT") ? e.getAmount() : e.getAmount().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional
    public LedgerEntry reserveDebit(Long accountId, String assetId, java.math.BigDecimal amount) {
        // Acquire row lock on position
        Position pos = positionRepository.findByAccountIdAndAssetIdForUpdate(accountId, assetId)
                .orElseGet(() -> {
                    Position p = new Position();
                    p.setAccountId(accountId);
                    p.setAssetId(assetId);
                    return positionRepository.save(p);
                });

        if (pos.getAvailable().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient funds");
        }

        pos.setAvailable(pos.getAvailable().subtract(amount));
        pos.setLocked(pos.getLocked().add(amount));
        pos.setUpdatedAt(OffsetDateTime.now());
        positionRepository.save(pos);

        LedgerEntry e = new LedgerEntry();
        e.setAccountId(accountId);
        e.setAssetId(assetId);
        e.setAmount(amount);
        e.setType("DEBIT");
        return ledgerRepo.save(e);
    }

    @Transactional
    public LedgerEntry settleWithdrawal(Long accountId, String assetId, java.math.BigDecimal amount) {
        // When a withdrawal settles, locked decreases and total decreases
        Position pos = positionRepository.findByAccountIdAndAssetIdForUpdate(accountId, assetId)
                .orElseThrow(() -> new IllegalStateException("Position not found"));

        if (pos.getLocked().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient locked funds");
        }

        pos.setLocked(pos.getLocked().subtract(amount));
        pos.setUpdatedAt(OffsetDateTime.now());
        positionRepository.save(pos);

        LedgerEntry e = new LedgerEntry();
        e.setAccountId(accountId);
        e.setAssetId(assetId);
        e.setAmount(amount);
        e.setType("SETTLEMENT");
        LedgerEntry saved = ledgerRepo.save(e);
        if (auditService != null) auditService.record("WithdrawalSettled", accountId == null ? null : accountId.toString(), "amount=" + amount.toPlainString());
        return saved;
    }

    @Transactional
    public LedgerEntry reverseReservation(Long accountId, String assetId, java.math.BigDecimal amount, String reason) {
        // Reverse a reservation (e.g., provider failure): locked -= amount; available += amount
        Position pos = positionRepository.findByAccountIdAndAssetIdForUpdate(accountId, assetId)
                .orElseThrow(() -> new IllegalStateException("Position not found"));

        if (pos.getLocked().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient locked funds to reverse");
        }

        pos.setLocked(pos.getLocked().subtract(amount));
        pos.setAvailable(pos.getAvailable().add(amount));
        pos.setUpdatedAt(OffsetDateTime.now());
        positionRepository.save(pos);

        LedgerEntry e = new LedgerEntry();
        e.setAccountId(accountId);
        e.setAssetId(assetId);
        e.setAmount(amount);
        e.setType("REVERSAL");
        e.setMetadata(reason);
        LedgerEntry saved = ledgerRepo.save(e);
        if (auditService != null) auditService.record("ReservationReversed", accountId == null ? null : accountId.toString(), "amount=" + amount.toPlainString() + " reason=" + reason);
        return saved;
    }
}
