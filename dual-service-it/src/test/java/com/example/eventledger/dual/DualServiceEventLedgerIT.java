package com.example.eventledger.dual;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Real dual-process end-to-end suite. Processes are started by Maven Failsafe /
 * process-exec-maven-plugin under {@code -Pdual-service-it}.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DualServiceEventLedgerIT {
  private static final String TRACE_ID = "11111111111111111111111111111111";
  private static final String TRACEPARENT = "00-" + TRACE_ID + "-2222222222222222-01";
  private static final String ACCOUNT_ID = "acct-dual-it";

  private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
  private static final HttpClient HTTP =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

  private static String gatewayBase;
  private static String accountBase;
  private static Path gatewayLog;
  private static Path accountLog;

  @BeforeAll
  static void waitForHealthyServices() {
    int gatewayPort = Integer.parseInt(System.getProperty("dual.it.gateway.port", "18080"));
    int accountPort = Integer.parseInt(System.getProperty("dual.it.account.port", "18081"));
    gatewayBase = "http://127.0.0.1:" + gatewayPort;
    accountBase = "http://127.0.0.1:" + accountPort;
    gatewayLog = Path.of(System.getProperty("dual.it.gateway.log", "target/dual-service-it/logs/event-gateway.log"));
    accountLog = Path.of(System.getProperty("dual.it.account.log", "target/dual-service-it/logs/account-service.log"));

    try {
      await()
          .atMost(Duration.ofSeconds(90))
          .pollInterval(Duration.ofMillis(500))
          .untilAsserted(
              () -> {
                assertHealth(gatewayBase + "/health", "event-gateway");
                assertHealth(accountBase + "/health", "account-service");
              });
    } catch (RuntimeException ex) {
      dumpLogs("services did not become healthy");
      throw ex;
    }
  }

  @Test
  @Order(1)
  void healthVerification() throws Exception {
    assertHealth(gatewayBase + "/health", "event-gateway");
    assertHealth(accountBase + "/health", "account-service");
  }

  @Test
  @Order(2)
  void newCredit_isApplied() throws Exception {
    HttpResponse<String> created = postEvent(credit("evt-dual-it-credit", "150.00", "2026-05-15T14:02:11Z"), null);

    assertThat(created.statusCode()).isEqualTo(201);
    JsonNode body = MAPPER.readTree(created.body());
    assertThat(body.path("processingStatus").asText()).isEqualTo("APPLIED");
    assertThat(balance()).isEqualByComparingTo("150.00");
  }

  @Test
  @Order(3)
  void exactDuplicate_replaysWithoutChangingBalance() throws Exception {
    HttpResponse<String> replay = postEvent(credit("evt-dual-it-credit", "150.00", "2026-05-15T14:02:11Z"), null);

    assertThat(replay.statusCode()).isEqualTo(200);
    assertThat(replay.headers().firstValue("Idempotent-Replay")).contains("true");
    JsonNode body = MAPPER.readTree(replay.body());
    assertThat(body.path("idempotentReplay").asBoolean()).isTrue();
    assertThat(balance()).isEqualByComparingTo("150.00");
  }

  @Test
  @Order(4)
  void conflictingDuplicate_returns409() throws Exception {
    HttpResponse<String> conflict = postEvent(credit("evt-dual-it-credit", "999.00", "2026-05-15T14:02:11Z"), null);

    assertThat(conflict.statusCode()).isEqualTo(409);
    JsonNode body = MAPPER.readTree(conflict.body());
    assertThat(body.path("errorCode").asText()).isEqualTo("CONFLICT");
    assertThat(balance()).isEqualByComparingTo("150.00");
  }

  @Test
  @Order(5)
  void debit_updatesBalanceExactly() throws Exception {
    Map<String, Object> debit = event("evt-dual-it-debit", ACCOUNT_ID, "DEBIT", "40.00", "2026-05-15T15:00:00Z");
    HttpResponse<String> created = postEvent(debit, null);

    assertThat(created.statusCode()).isEqualTo(201);
    assertThat(MAPPER.readTree(created.body()).path("processingStatus").asText()).isEqualTo("APPLIED");
    assertThat(balance()).isEqualByComparingTo("110.00");
  }

  @Test
  @Order(6)
  void outOfOrderEvents_listChronologically() throws Exception {
    assertThat(postEvent(credit("evt-dual-it-late", "5.00", "2026-05-16T12:00:00Z"), null).statusCode())
        .isEqualTo(201);
    assertThat(postEvent(credit("evt-dual-it-early", "3.00", "2026-05-14T08:00:00Z"), null).statusCode())
        .isEqualTo(201);

    HttpResponse<String> listed = get(gatewayBase + "/events?account=" + ACCOUNT_ID);
    assertThat(listed.statusCode()).isEqualTo(200);
    JsonNode events = MAPPER.readTree(listed.body());
    assertThat(events.isArray()).isTrue();
    assertThat(events.size()).isGreaterThanOrEqualTo(4);

    List<String> timestamps = new ArrayList<>();
    for (JsonNode event : events) {
      timestamps.add(event.path("eventTimestamp").asText());
    }
    assertThat(timestamps).isSorted();

    // Tie-break sample: early before late among the two OOO credits.
    List<String> ids = new ArrayList<>();
    for (JsonNode event : events) {
      ids.add(event.path("eventId").asText());
    }
    assertThat(ids.indexOf("evt-dual-it-early")).isLessThan(ids.indexOf("evt-dual-it-late"));
    assertThat(balance()).isEqualByComparingTo("118.00");
  }

  @Test
  @Order(7)
  void crossServicePersistence_agreesOnUniqueTransactions() throws Exception {
    HttpResponse<String> gatewayEvent = get(gatewayBase + "/events/evt-dual-it-credit");
    assertThat(gatewayEvent.statusCode()).isEqualTo(200);
    assertThat(MAPPER.readTree(gatewayEvent.body()).path("processingStatus").asText()).isEqualTo("APPLIED");

    HttpResponse<String> account = get(accountBase + "/accounts/" + ACCOUNT_ID);
    assertThat(account.statusCode()).isEqualTo(200);
    JsonNode history = MAPPER.readTree(account.body()).path("transactions");
    assertThat(history.isArray()).isTrue();

    long creditCopies =
        history.findValuesAsText("eventId").stream().filter(id -> id.equals("evt-dual-it-credit")).count();
    assertThat(creditCopies).isEqualTo(1);
    assertThat(history.size()).isEqualTo(4);
    assertThat(balance()).isEqualByComparingTo("118.00");
  }

  @Test
  @Order(8)
  void tracePropagation_correlatesStructuredLogsAcrossServices() throws Exception {
    HttpResponse<String> created =
        postEvent(credit("evt-dual-it-trace", "1.00", "2026-05-17T10:00:00Z"), TRACEPARENT);

    assertThat(created.statusCode()).isEqualTo(201);
    assertThat(MAPPER.readTree(created.body()).path("processingStatus").asText()).isEqualTo("APPLIED");

    await()
        .atMost(Duration.ofSeconds(20))
        .pollInterval(Duration.ofMillis(200))
        .untilAsserted(
            () -> {
              JsonNode gatewayAudit = findAudit(gatewayLog, TRACE_ID, "submit_event", "evt-dual-it-trace");
              JsonNode accountAudit = findAudit(accountLog, TRACE_ID, "apply_transaction", "evt-dual-it-trace");
              assertThat(gatewayAudit.path("traceId").asText()).isEqualTo(TRACE_ID);
              assertThat(accountAudit.path("traceId").asText()).isEqualTo(TRACE_ID);
              String gatewaySpan = gatewayAudit.path("spanId").asText();
              String accountSpan = accountAudit.path("spanId").asText();
              assertThat(gatewaySpan).isNotBlank();
              assertThat(accountSpan).isNotBlank();
              assertThat(gatewaySpan).isNotEqualTo(accountSpan);
              assertThat(gatewayAudit.path("service").asText()).isEqualTo("event-gateway");
              assertThat(accountAudit.path("service").asText()).isEqualTo("account-service");
            });

    assertThat(balance()).isEqualByComparingTo("119.00");
  }

  private static void assertHealth(String url, String expectedService) throws Exception {
    HttpResponse<String> response = get(url);
    assertThat(response.statusCode()).as("health %s", url).isEqualTo(200);
    JsonNode body = MAPPER.readTree(response.body());
    assertThat(body.path("service").asText()).isEqualTo(expectedService);
    assertThat(body.path("database").path("status").asText()).isEqualTo("UP");
  }

  private static BigDecimal balance() throws Exception {
    HttpResponse<String> response = get(accountBase + "/accounts/" + ACCOUNT_ID + "/balance");
    assertThat(response.statusCode()).isEqualTo(200);
    return new BigDecimal(MAPPER.readTree(response.body()).path("balance").asText());
  }

  private static Map<String, Object> credit(String eventId, String amount, String timestamp) {
    return event(eventId, ACCOUNT_ID, "CREDIT", amount, timestamp);
  }

  private static Map<String, Object> event(
      String eventId, String accountId, String type, String amount, String timestamp) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("eventId", eventId);
    body.put("accountId", accountId);
    body.put("type", type);
    body.put("amount", new BigDecimal(amount));
    body.put("currency", "USD");
    body.put("eventTimestamp", timestamp);
    return body;
  }

  private static HttpResponse<String> postEvent(Map<String, Object> body, String traceparent) throws Exception {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(URI.create(gatewayBase + "/events"))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)));
    if (traceparent != null) {
      builder.header("traceparent", traceparent);
    }
    return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
  }

  private static HttpResponse<String> get(String url) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).GET().build();
    return HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
  }

  private static JsonNode findAudit(Path logFile, String traceId, String operation, String eventId)
      throws IOException {
    assertThat(logFile).exists();
    String content = Files.readString(logFile, StandardCharsets.UTF_8);
    for (String line : content.split("\\R")) {
      String trimmed = line.trim();
      if (!trimmed.startsWith("{")
          || !trimmed.contains(traceId)
          || !trimmed.contains(operation)
          || !trimmed.contains(eventId)) {
        continue;
      }
      JsonNode node = MAPPER.readTree(trimmed);
      assertThat(node.isObject()).as("log line must be JSON: %s", trimmed).isTrue();
      return node;
    }
    dumpLogs("missing audit log operation=" + operation + " eventId=" + eventId);
    throw new AssertionError("no audit log for operation=" + operation + " eventId=" + eventId + " in " + logFile);
  }

  private static void dumpLogs(String reason) {
    System.err.println("=== Dual-service IT diagnostics: " + reason + " ===");
    dumpLog("gateway", gatewayLog);
    dumpLog("account", accountLog);
  }

  private static void dumpLog(String name, Path path) {
    System.err.println("--- " + name + " log: " + path + " ---");
    try {
      if (path == null || !Files.exists(path)) {
        System.err.println("(missing)");
        return;
      }
      List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
      int from = Math.max(0, lines.size() - 80);
      for (String line : lines.subList(from, lines.size())) {
        System.err.println(line);
      }
    } catch (IOException ex) {
      System.err.println("failed reading log: " + ex.getMessage());
    }
  }
}
