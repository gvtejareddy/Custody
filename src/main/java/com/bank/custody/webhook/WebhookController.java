package com.bank.custody.webhook;

import com.bank.custody.fireblocks.WebhookVerifier;
import com.bank.custody.outbox.OutboxEvent;
import com.bank.custody.outbox.OutboxRepository;
import com.bank.custody.providerevent.ProviderEvent;
import com.bank.custody.providerevent.ProviderEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Webhook receiver for provider events. Persists provider events and creates
 * an outbox record for downstream processing. Signature verification is a
 * TODO for production and should be implemented according to provider docs.
 */
@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {

    private final ProviderEventRepository providerEventRepository;
    private final OutboxRepository outboxRepository;
    private final WebhookVerifier verifier;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WebhookController(ProviderEventRepository providerEventRepository,
                             OutboxRepository outboxRepository,
                             @org.springframework.beans.factory.annotation.Autowired(required = false) WebhookVerifier verifier) {
        this.providerEventRepository = providerEventRepository;
        this.outboxRepository = outboxRepository;
        this.verifier = verifier;
    }

    @PostMapping("/fireblocks")
    @Transactional
    public ResponseEntity<String> handleFireblocksWebhook(@RequestBody Map<String, Object> payload,
                                                          @RequestHeader Map<String, String> headers) {
        // Verify signature if a verifier is present
        if (verifier != null) {
            boolean ok = verifier.verify(payload, headers);
            if (!ok) {
                return new ResponseEntity<>("invalid signature", HttpStatus.UNAUTHORIZED);
            }
        }

        String provider = "fireblocks";
        String providerEventId = payload.getOrDefault("id", payload.getOrDefault("eventId", "")).toString();
        String eventType = payload.getOrDefault("type", payload.getOrDefault("eventType", "unknown")).toString();

        try {
            String json = objectMapper.writeValueAsString(payload);

            // dedupe: if already exists, return 200 OK
            if (providerEventRepository.findByProviderAndProviderEventId(provider, providerEventId).isPresent()) {
                return new ResponseEntity<>("duplicate", HttpStatus.OK);
            }

            ProviderEvent ev = new ProviderEvent(provider, providerEventId, eventType, json);
            providerEventRepository.save(ev);

            OutboxEvent out = new OutboxEvent("provider_event", ev.getId().toString(), "provider.event.received", json);
            outboxRepository.save(out);

            return new ResponseEntity<>("ok", HttpStatus.OK);
        } catch (JsonProcessingException e) {
            return new ResponseEntity<>("invalid payload", HttpStatus.BAD_REQUEST);
        }
    }
}
