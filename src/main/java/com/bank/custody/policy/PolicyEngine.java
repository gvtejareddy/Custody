package com.bank.custody.policy;

import com.bank.custody.transaction.Transaction;

public interface PolicyEngine {
    PolicyDecision evaluate(Transaction tx);

    class PolicyDecision {
        public final String decision; // ALLOW, DENY, REVIEW
        public final String[] rules;

        public PolicyDecision(String decision, String[] rules) {
            this.decision = decision;
            this.rules = rules;
        }
    }
}
