package com.bank.custody.compliance;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class NoopComplianceProvider implements ComplianceProvider {

    @Override
    public ScreeningResult screenWithdrawal(Long accountId, String assetId, BigDecimal amount, String destinationAddress) {
        return new ScreeningResult(true, "noop");
    }

    @Override
    public ScreeningResult screenDeposit(Long accountId, String assetId, BigDecimal amount, String sourceAddress) {
        return new ScreeningResult(true, "noop");
    }
}
