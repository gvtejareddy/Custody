package com.bank.custody.e2e;

import com.bank.custody.ledger.LedgerService;
import com.bank.custody.outbox.LocalOutboxProcessor;
import com.bank.custody.position.PositionRepository;
import com.bank.custody.providerevent.ProviderEventRepository;
import com.bank.custody.reconcile.ReconciliationRepository;
import com.bank.custody.transaction.Transaction;
import com.bank.custody.transaction.TransactionRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = {com.bank.custody.Application.class, com.bank.custody.TestSecurityConfiguration.class})
@Testcontainers
@ActiveProfiles("test")
public class E2EAcceptanceTest {

    @Container
    @SuppressWarnings("resource")
    public static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("custody_test")
            .withUsername("sa")
            .withPassword("sa");

    static WireMockServer wireMock;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> postgres.getJdbcUrl());
        r.add("spring.datasource.username", () -> postgres.getUsername());
        r.add("spring.datasource.password", () -> postgres.getPassword());
        r.add("fireblocks.base-path", () -> "http://localhost:8089/v1");
        r.add("fireblocks.api-key", () -> "test-key");
    }

    @BeforeAll
    public static void startWiremock() {
        wireMock = new WireMockServer(8089);
        wireMock.start();
        WireMock.configureFor("localhost", 8089);
        // vault create
        WireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/vault/accounts"))
                .willReturn(WireMock.aResponse().withStatus(200).withHeader("Content-Type","application/json")
                        .withBody("{\"id\":\"vault-123\"}")));
        // transaction create
        WireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/transactions"))
                .willReturn(WireMock.aResponse().withStatus(200).withHeader("Content-Type","application/json")
                        .withBody("{\"id\":\"tx-123\", \"status\": \"SUBMITTED\"}")));
    }

    @AfterAll
    public static void stopWiremock() {
        if (wireMock != null) wireMock.stop();
    }

    @LocalServerPort
    int port;

    RestTemplate rest = new RestTemplate();

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("local-dev-token");
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Autowired
    ProviderEventRepository providerEventRepository;

    @Autowired
    TransactionRepository txRepo;

    @Autowired
    PositionRepository positionRepository;

    @Autowired
    LocalOutboxProcessor localOutboxProcessor;

    @Autowired
    LedgerService ledgerService;

    @Autowired
    ReconciliationRepository reconciliationRepository;

    @Autowired
    com.bank.custody.reconcile.ReconciliationService reconciliationService;

    @Test
    public void e2e_happy_path_and_concurrent_withdrawals() throws Exception {
        // 1-3 Create customer/account
        String base = "http://localhost:"+port;
        ResponseEntity<Map<String,Object>> accResp = rest.exchange(base + "/api/v1/accounts",
            HttpMethod.POST,
            new HttpEntity<>(Map.of("externalCustomerId","C001","name","Customer 1"), authHeaders()),
            new ParameterizedTypeReference<Map<String,Object>>(){});
        Assertions.assertEquals(HttpStatus.CREATED, accResp.getStatusCode());
        Number accountIdNum = (Number) accResp.getBody().get("id");
        Long accountId = accountIdNum.longValue();

        // 4 Configure BTC
        ResponseEntity<Map<String,Object>> assetResp = rest.exchange(base + "/api/v1/assets",
            HttpMethod.POST,
            new HttpEntity<>(Map.of("symbol","BTC","name","Bitcoin","network","BITCOIN","active",true), authHeaders()),
            new ParameterizedTypeReference<Map<String,Object>>(){});
        Assertions.assertEquals(HttpStatus.CREATED, assetResp.getStatusCode());

        // 5-6 Create/retrieve Fireblocks wallet mapping + deposit address
        ResponseEntity<Map<String,Object>> addrResp = rest.exchange(base + "/api/v1/custody-accounts/"+accountId+"/deposit-addresses",
            HttpMethod.POST,
            new HttpEntity<>(Map.of("asset","BTC","network","BITCOIN"), authHeaders()),
            new ParameterizedTypeReference<Map<String,Object>>(){});
        Assertions.assertTrue(addrResp.getStatusCode().is2xxSuccessful());
        String address = (String) addrResp.getBody().get("address");

        // 7 Simulate Fireblocks deposit event
        Map<String,Object> depositEvt = Map.of("id","evt-dep-1","type","deposit","address", address, "amount","1","status","confirmed");
        HttpHeaders headers = authHeaders();
        ResponseEntity<String> wh = rest.postForEntity(base + "/api/v1/webhooks/fireblocks", new HttpEntity<>(depositEvt, headers), String.class);
        Assertions.assertEquals(HttpStatus.OK, wh.getStatusCode());

        // process outbox manually
        localOutboxProcessor.processPending();

        // 8-13 Verify provider event stored and deposit transaction created and position credited
        Assertions.assertTrue(providerEventRepository.findByProviderAndProviderEventId("fireblocks","evt-dep-1").isPresent());
        // find transaction
        Thread.sleep(200); // small wait for processing
        var txs = txRepo.findAll();
        Assertions.assertFalse(txs.isEmpty());
        Transaction depositTx = txs.stream().filter(t->"IN".equals(t.getDirection())).findFirst().orElseThrow();
        Assertions.assertEquals(0, depositTx.getAmount().compareTo(new java.math.BigDecimal("1")));

        var posOpt = positionRepository.findByAccountIdAndAssetId(accountId, "BTC");
        Assertions.assertTrue(posOpt.isPresent());
        Assertions.assertEquals(0, posOpt.get().getAvailable().compareTo(new java.math.BigDecimal("1")));

        // 14-20 Request withdrawal 0.4 BTC
        HttpHeaders reqHeaders = authHeaders(); reqHeaders.set("Idempotency-Key", "idemp-1");
        HttpEntity<Map<String,Object>> withdrawReq = new HttpEntity<>(Map.of("asset","BTC","network","BITCOIN","amount","0.4","destinationAddress","bc1..."), reqHeaders);
        ResponseEntity<Map<String,Object>> wresp = rest.exchange(base + "/api/v1/custody-accounts/"+accountId+"/withdrawals",
            HttpMethod.POST,
            withdrawReq,
            new ParameterizedTypeReference<Map<String,Object>>(){});
        Assertions.assertEquals(HttpStatus.CREATED, wresp.getStatusCode());
        Map<String,Object> body = wresp.getBody();
        Assertions.assertNotNull(body.get("externalProviderTxId"));

        // simulate provider confirmation by settling
        var outTx = txRepo.findByIdempotencyKey("idemp-1").orElseThrow();
        ledgerService.settleWithdrawal(outTx.getAccountId(), outTx.getAssetId(), outTx.getAmount());
        outTx.setStatus("SETTLED"); txRepo.save(outTx);

        // verify final position 0.6
        var pos = positionRepository.findByAccountIdAndAssetId(outTx.getAccountId(), outTx.getAssetId()).orElseThrow();
        Assertions.assertEquals(0, pos.getAvailable().compareTo(new java.math.BigDecimal("0.6")));

        // 24 Run reconciliation
        reconciliationRepository.deleteAll();
        // call reconciliation directly
        reconciliationService.runDailyReconciliation();
        Assertions.assertFalse(reconciliationRepository.findAll().isEmpty());

        // Concurrent withdrawal test: two simultaneous withdrawals of 0.7 BTC should allow only one
        // top up to 1 BTC available
        // create two threads
        ExecutorService svc = Executors.newFixedThreadPool(2);
        Callable<Boolean> c1 = () -> {
            HttpHeaders h = authHeaders(); h.set("Idempotency-Key", "c1");
            HttpEntity<Map<String,Object>> r = new HttpEntity<>(Map.of("asset","BTC","network","BITCOIN","amount","0.7","destinationAddress","bc1a"), h);
                ResponseEntity<Map<String,Object>> resp = rest.exchange(base + "/api/v1/custody-accounts/"+accountId+"/withdrawals",
                    HttpMethod.POST,
                    r,
                    new ParameterizedTypeReference<Map<String,Object>>(){});
            return resp.getStatusCode()==HttpStatus.CREATED;
        };
        Callable<Boolean> c2 = () -> {
            HttpHeaders h = authHeaders(); h.set("Idempotency-Key", "c2");
            HttpEntity<Map<String,Object>> r = new HttpEntity<>(Map.of("asset","BTC","network","BITCOIN","amount","0.7","destinationAddress","bc1b"), h);
                ResponseEntity<Map<String,Object>> resp = rest.exchange(base + "/api/v1/custody-accounts/"+accountId+"/withdrawals",
                    HttpMethod.POST,
                    r,
                    new ParameterizedTypeReference<Map<String,Object>>(){});
            return resp.getStatusCode()==HttpStatus.CREATED;
        };

        Future<Boolean> f1 = svc.submit(c1);
        Future<Boolean> f2 = svc.submit(c2);
        int success = (f1.get()?1:0) + (f2.get()?1:0);
        Assertions.assertEquals(1, success);
        svc.shutdownNow();
    }
}
