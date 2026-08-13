package com.bank.custody.fireblocks;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.MediaType;
import reactor.core.publisher.Mono;
import org.springframework.core.ParameterizedTypeReference;

import java.util.concurrent.CompletableFuture;
import java.util.Map;

@Service
public class FireblocksService {
    private final WebClient webClient;
    private final FireblocksProperties props;
    private final FireblocksRequestSigner signer;

    public FireblocksService(@Qualifier("fireblocksWebClient") WebClient fireblocksWebClient, FireblocksProperties props, FireblocksRequestSigner signer) {
        this.webClient = fireblocksWebClient;
        this.props = props;
        this.signer = signer;
    }

    public CompletableFuture<Map<String,Object>> createVaultAccount(Map<String, Object> req, String idempotencyKey) {
        WebClient.RequestBodySpec r = webClient.post()
                .uri(uriBuilder -> uriBuilder.path("/vault/accounts").build())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON);

        if (props.getApiKey() != null) r.header("X-API-Key", props.getApiKey());
        if (idempotencyKey != null) r.header("X-Idempotency-Key", idempotencyKey);

        if (signer != null) {
            try {
                String sig = signer.sign(req);
                if (sig != null) r.header("X-Fireblocks-Signature", sig);
            } catch (Exception ignored) {}
        }
        Mono<Map<String,Object>> resp = r.bodyValue(req).retrieve().bodyToMono(new ParameterizedTypeReference<Map<String,Object>>(){});
        return resp.toFuture();
    }

    public CompletableFuture<Map<String,Object>> createTransaction(Map<String, Object> req, String idempotencyKey) {
        WebClient.RequestBodySpec r = webClient.post()
                .uri(uriBuilder -> uriBuilder.path("/transactions").build())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON);

        if (props.getApiKey() != null) r.header("X-API-Key", props.getApiKey());
        if (idempotencyKey != null) r.header("X-Idempotency-Key", idempotencyKey);

        if (signer != null) {
            try {
                String sig = signer.sign(req);
                if (sig != null) r.header("X-Fireblocks-Signature", sig);
            } catch (Exception ignored) {}
        }
        Mono<Map<String,Object>> resp = r.bodyValue(req).retrieve().bodyToMono(new ParameterizedTypeReference<Map<String,Object>>(){});
        return resp.toFuture();
    }
}

