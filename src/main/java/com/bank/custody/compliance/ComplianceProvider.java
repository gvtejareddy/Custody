package com.bank.custody.compliance;

import java.math.BigDecimal;

public interface ComplianceProvider {
    ScreeningResult screenWithdrawal(Long accountId, String assetId, BigDecimal amount, String destinationAddress);
    ScreeningResult screenDeposit(Long accountId, String assetId, BigDecimal amount, String sourceAddress);

    public static class ScreeningResult {
        public final boolean passed;
        public final String reason;

        public ScreeningResult(boolean passed, String reason) {
            this.passed = passed;
            this.reason = reason;
        }
    }
}
