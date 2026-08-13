package com.bank.custody.fireblocks;

import java.util.Map;

public interface WebhookVerifier {
    boolean verify(Map<String, Object> payload, Map<String, String> headers);
}
