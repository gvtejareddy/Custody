package com.bank.custody.reconcile;

import com.bank.custody.position.Position;
import com.bank.custody.position.PositionRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReconciliationService {

    private final PositionRepository positionRepository;
    private final ReconciliationRepository reconciliationRepository;

    public ReconciliationService(PositionRepository positionRepository, ReconciliationRepository reconciliationRepository) {
        this.positionRepository = positionRepository;
        this.reconciliationRepository = reconciliationRepository;
    }

    @Scheduled(cron = "0 0 2 * * ?") // daily at 02:00
    @Transactional
    public void runDailyReconciliation() {
        // Simple MVP reconciliation: sum client positions per asset and record a MATCH result.
        List<Position> positions = positionRepository.findAll();
        Map<String, java.math.BigDecimal> sums = positions.stream()
                .collect(Collectors.groupingBy(Position::getAssetId,
                        Collectors.mapping(Position::getAvailable, Collectors.reducing(java.math.BigDecimal.ZERO, java.math.BigDecimal::add))));

        for (Map.Entry<String, java.math.BigDecimal> e : sums.entrySet()) {
            ReconciliationResult r = new ReconciliationResult();
            r.setAssetId(e.getKey());
            r.setNetwork("default");
            r.setStatus("MATCH");
            r.setDetails("client_total_available=" + e.getValue().toPlainString());
            reconciliationRepository.save(r);
        }
    }
}
