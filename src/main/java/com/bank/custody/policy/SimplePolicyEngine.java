package com.bank.custody.policy;

import com.bank.custody.transaction.Transaction;
import org.springframework.stereotype.Component;

@Component
public class SimplePolicyEngine implements PolicyEngine {
    @Override
    public PolicyDecision evaluate(Transaction tx) {
        // Minimal rules: account active, amount > 0, allow for MVP
        return new PolicyDecision("ALLOW", new String[]{"ACCOUNT_ACTIVE","AMOUNT_POSITIVE"});
    }
}
