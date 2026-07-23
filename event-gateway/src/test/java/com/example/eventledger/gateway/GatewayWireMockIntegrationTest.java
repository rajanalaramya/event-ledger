package com.example.eventledger.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GatewayWireMockIntegrationTest {
  private static final WireMockServer WIREMOCK = startWireMock();

  @DynamicPropertySource
  static void accountServiceUrl(DynamicPropertyRegistry registry) {
    registry.add("eventledger.account-service.base-url", WIREMOCK::baseUrl);
  }

  @LocalServerPort private int port;
  @Autowired private TestRestTemplate http;
  @Autowired private ObjectMapper mapper;
  @Autowired private EventRepository events;
  @Autowired private CircuitBreakerRegistry circuitBreakers;

  @BeforeEach
  void resetState() {
    WIREMOCK.resetAll();
    events.deleteAll();
    circuitBreakers.circuitBreaker("accountService").reset();
  }

  @AfterAll
  static void stopWireMock() {
    WIREMOCK.stop();
  }

  @Test
  void happyPath_createsAndAppliesEvent() {
    stubSuccessfulApply();

    ResponseEntity<JsonNode> created = post(event("happy-1", "acct-happy", "10.00", "2025-01-01T12:00:00Z"));

    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(created.getHeaders().getLocation()).hasToString("/events/happy-1");
    assertThat(created.getBody().path("processingStatus").asText()).isEqualTo("APPLIED");
    assertThat(get("/events/happy-1").getBody().path("processingStatus").asText()).isEqualTo("APPLIED");
  }

  @Test
  void duplicateIdentical_returns200WithoutSecondAccountCall() {
    stubSuccessfulApply();
    Map<String, Object> request = event("duplicate-1", "acct-duplicate", "10.00", "2025-01-01T12:00:00Z");

    assertThat(post(request).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    ResponseEntity<JsonNode> replay = post(request);

    assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(replay.getHeaders().getFirst("Idempotent-Replay")).isEqualTo("true");
    assertThat(replay.getBody().path("idempotentReplay").asBoolean()).isTrue();
    WIREMOCK.verify(1, postRequestedFor(urlPathMatching("/accounts/.*/transactions")));
  }

  @Test
  void conflictingPayload_returns409() {
    stubSuccessfulApply();
    assertThat(post(event("conflict-1", "acct-conflict", "10.00", "2025-01-01T12:00:00Z")).getStatusCode()).isEqualTo(HttpStatus.CREATED);

    ResponseEntity<JsonNode> conflict = post(event("conflict-1", "acct-conflict", "11.00", "2025-01-01T12:00:00Z"));

    assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(conflict.getBody().path("errorCode").asText()).isEqualTo("CONFLICT");
  }

  @Test
  void outOfOrderEvents_listChronologically() {
    stubSuccessfulApply();
    assertThat(post(event("later", "acct-list", "10.00", "2025-01-02T12:00:00Z")).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(post(event("earlier", "acct-list", "10.00", "2025-01-01T12:00:00Z")).getStatusCode()).isEqualTo(HttpStatus.CREATED);

    ResponseEntity<JsonNode> listed = get("/events?account=acct-list");

    assertThat(listed.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(listed.getBody().get(0).path("eventId").asText()).isEqualTo("earlier");
    assertThat(listed.getBody().get(1).path("eventId").asText()).isEqualTo("later");
  }

  @Test
  void validationError_returnsProblemDetail() {
    ResponseEntity<JsonNode> invalid = post(event(" ", "acct-validation", "10.00", "2025-01-01T12:00:00Z"));

    assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(invalid.getBody().path("errorCode").asText()).isEqualTo("VALIDATION_ERROR");
    assertThat(invalid.getBody().path("errors").path("eventId").asText()).isNotBlank();
  }

  @Test
  void accountUnavailable_storesRetryableAndAllowsRead() {
    WIREMOCK.stubFor(WireMock.post(urlPathMatching("/accounts/.*/transactions")).willReturn(aResponse().withStatus(503)));

    ResponseEntity<JsonNode> unavailable = post(event("unavailable-1", "acct-unavailable", "10.00", "2025-01-01T12:00:00Z"));

    assertThat(unavailable.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(get("/events/unavailable-1").getBody().path("processingStatus").asText()).isEqualTo("RETRYABLE_FAILURE");
  }

  @Test
  void recoveryAfterOutage_resubmitsSafely() {
    WIREMOCK.stubFor(WireMock.post(urlPathMatching("/accounts/.*/transactions"))
        .inScenario("outage").whenScenarioStateIs(Scenario.STARTED)
        .willReturn(aResponse().withStatus(503)).willSetStateTo("recovered"));
    WIREMOCK.stubFor(WireMock.post(urlPathMatching("/accounts/.*/transactions"))
        .inScenario("outage").whenScenarioStateIs("recovered").willReturn(successResponse(true)));
    Map<String, Object> request = event("recover-1", "acct-recover", "10.00", "2025-01-01T12:00:00Z");

    assertThat(post(request).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    ResponseEntity<JsonNode> recovered = post(request);

    assertThat(recovered.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(recovered.getBody().path("processingStatus").asText()).isEqualTo("APPLIED");
    WIREMOCK.verify(2, postRequestedFor(urlPathMatching("/accounts/.*/transactions")));
  }

  @Test
  void timeout_marksRetryable() {
    WIREMOCK.stubFor(WireMock.post(urlPathMatching("/accounts/.*/transactions"))
        .willReturn(successResponse(false).withFixedDelay(1_000)));

    ResponseEntity<JsonNode> timeout = post(event("timeout-1", "acct-timeout", "10.00", "2025-01-01T12:00:00Z"));

    assertThat(timeout.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(get("/events/timeout-1").getBody().path("processingStatus").asText()).isEqualTo("RETRYABLE_FAILURE");
  }

  @Test
  void noRetryOnConflict409() {
    WIREMOCK.stubFor(WireMock.post(urlPathMatching("/accounts/.*/transactions")).willReturn(aResponse().withStatus(409)));

    ResponseEntity<JsonNode> conflict = post(event("account-conflict-1", "acct-account-conflict", "10.00", "2025-01-01T12:00:00Z"));

    assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    WIREMOCK.verify(1, postRequestedFor(urlPathMatching("/accounts/.*/transactions")));
  }

  @Test
  void tracePropagation_sendsTraceparent() {
    stubSuccessfulApply();
    String traceparent = "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01";

    ResponseEntity<JsonNode> created = post(event("trace-1", "acct-trace", "10.00", "2025-01-01T12:00:00Z"), traceparent);

    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    WIREMOCK.verify(postRequestedFor(urlPathMatching("/accounts/.*/transactions"))
        .withHeader("traceparent", matching("00-[0-9a-f]{32}-[0-9a-f]{16}-0[01]")));
  }

  @Test
  void uncertainCommit_timeoutThenReplay() {
    WIREMOCK.stubFor(WireMock.post(urlPathMatching("/accounts/.*/transactions"))
        .inScenario("uncertain-commit").whenScenarioStateIs(Scenario.STARTED)
        .willReturn(successResponse(false).withFixedDelay(1_000)).willSetStateTo("committed"));
    WIREMOCK.stubFor(WireMock.post(urlPathMatching("/accounts/.*/transactions"))
        .inScenario("uncertain-commit").whenScenarioStateIs("committed").willReturn(successResponse(true)));
    Map<String, Object> request = event("uncertain-1", "acct-uncertain", "10.00", "2025-01-01T12:00:00Z");

    assertThat(post(request).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    ResponseEntity<JsonNode> replay = post(request);

    assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(replay.getBody().path("processingStatus").asText()).isEqualTo("APPLIED");
    // The account endpoint sees two HTTP requests, but the second response represents its idempotent replay.
    WIREMOCK.verify(2, postRequestedFor(urlPathMatching("/accounts/.*/transactions")));
  }

  @Test
  void circuitBreaker_opensAfterRepeatedFailures() {
    WIREMOCK.stubFor(WireMock.post(urlPathMatching("/accounts/.*/transactions")).willReturn(aResponse().withStatus(503)));

    assertThat(post(event("cb-1", "acct-cb", "10.00", "2025-01-01T12:00:00Z")).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(post(event("cb-2", "acct-cb", "10.00", "2025-01-01T12:01:00Z")).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(post(event("cb-3", "acct-cb", "10.00", "2025-01-01T12:02:00Z")).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

    ResponseEntity<JsonNode> fastFailure = post(event("cb-4", "acct-cb", "10.00", "2025-01-01T12:03:00Z"));

    assertThat(fastFailure.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(circuitBreakers.circuitBreaker("accountService").getState().name()).isEqualTo("OPEN");
    WIREMOCK.verify(3, postRequestedFor(urlPathMatching("/accounts/.*/transactions")));
  }

  @Nested
  class ConcurrentDuplicateGatewayTest {
    @Test
    void parallelIdenticalPosts_createOnlyOneEventRow() throws Exception {
      stubSuccessfulApply();
      Map<String, Object> request = event("concurrent-1", "acct-concurrent", "10.00", "2025-01-01T12:00:00Z");
      CountDownLatch ready = new CountDownLatch(2);
      CountDownLatch start = new CountDownLatch(1);
      try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
        Future<ResponseEntity<JsonNode>> first = executor.submit(() -> parallelPost(request, ready, start));
        Future<ResponseEntity<JsonNode>> second = executor.submit(() -> parallelPost(request, ready, start));
        ready.await();
        start.countDown();

        assertThat(first.get().getStatusCode().is2xxSuccessful() || second.get().getStatusCode().is2xxSuccessful()).isTrue();
      }

      assertThat(events.count()).isEqualTo(1);
      WIREMOCK.verify(1, postRequestedFor(urlPathMatching("/accounts/.*/transactions")));
    }
  }

  private void stubSuccessfulApply() {
    WIREMOCK.stubFor(WireMock.post(urlPathMatching("/accounts/.*/transactions")).willReturn(successResponse(false)));
  }

  private ResponseDefinitionBuilder successResponse(boolean idempotentReplay) {
    return aResponse().withStatus(201).withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .withBody("""
            {"eventId":"account-event","accountId":"account-id","type":"CREDIT","amount":10.00,
             "currency":"USD","balanceAfter":110.00,"appliedAt":"2025-01-01T12:00:00Z",
             "idempotentReplay":%s}
            """.formatted(idempotentReplay));
  }

  private Map<String, Object> event(String eventId, String accountId, String amount, String timestamp) {
    return Map.of("eventId", eventId, "accountId", accountId, "type", "CREDIT",
        "amount", new BigDecimal(amount), "currency", "USD", "eventTimestamp", Instant.parse(timestamp));
  }

  private ResponseEntity<JsonNode> post(Map<String, Object> body) {
    return post(body, null);
  }

  private ResponseEntity<JsonNode> post(Map<String, Object> body, String traceparent) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    if (traceparent != null) headers.set("traceparent", traceparent);
    return http.exchange(url("/events"), HttpMethod.POST, new HttpEntity<>(write(body), headers), JsonNode.class);
  }

  private ResponseEntity<JsonNode> parallelPost(Map<String, Object> body, CountDownLatch ready, CountDownLatch start) {
    ready.countDown();
    try {
      start.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError("parallel request was interrupted", e);
    }
    return post(body);
  }

  private ResponseEntity<JsonNode> get(String path) {
    return http.getForEntity(url(path), JsonNode.class);
  }

  private String write(Map<String, Object> body) {
    try {
      return mapper.writeValueAsString(body);
    } catch (Exception e) {
      throw new AssertionError("could not serialize test request", e);
    }
  }

  private String url(String path) {
    return "http://localhost:" + port + path;
  }

  private static WireMockServer startWireMock() {
    WireMockServer server = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
    server.start();
    return server;
  }
}
