package com.example.eventledger.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureObservability
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class AccountTracingLogIntegrationTest {
  private static final String TRACE_ID = "11111111111111111111111111111111";
  private static final String SPAN_ID = "2222222222222222";
  private static final String TRACEPARENT = "00-" + TRACE_ID + "-" + SPAN_ID + "-01";

  @LocalServerPort private int port;
  @Autowired private TestRestTemplate http;
  @Autowired private AccountRepository accounts;
  @Autowired private AppliedTransactionRepository transactions;
  @Autowired private ObjectMapper mapper;

  @BeforeEach
  void clear() {
    transactions.deleteAll();
    accounts.deleteAll();
  }

  @Test
  void applyTransaction_logsInboundTraceIdAsStructuredJsonFields(CapturedOutput output) throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("traceparent", TRACEPARENT);
    Map<String, Object> body =
        Map.of(
            "eventId", "evt-trace-account",
            "accountId", "acct-trace",
            "type", "CREDIT",
            "amount", 10.00,
            "currency", "USD",
            "eventTimestamp", "2026-01-01T00:00:00Z",
            "fingerprint", "fp-trace-account");

    ResponseEntity<String> response =
        http.postForEntity(
            "http://localhost:" + port + "/accounts/acct-trace/transactions",
            new HttpEntity<>(mapper.writeValueAsString(body), headers),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

    JsonNode audit = findAuditLine(output.getAll(), "evt-trace-account");
    assertThat(audit.isObject()).isTrue();
    assertThat(audit.path("traceId").asText()).isEqualTo(TRACE_ID);
    assertThat(audit.path("spanId").asText()).isNotBlank().isNotEqualTo(SPAN_ID);
    assertThat(audit.path("service").asText()).isEqualTo("account-service");
    assertThat(audit.path("operation").asText()).isEqualTo("apply_transaction");
    assertThat(audit.path("eventId").asText()).isEqualTo("evt-trace-account");
    assertThat(audit.path("accountId").asText()).isEqualTo("acct-trace");
    assertThat(audit.path("outcome").asText()).isEqualTo("APPLIED");
    // Guard against message-only embedding: required correlators must be top-level JSON fields.
    assertThat(audit.hasNonNull("traceId")).isTrue();
    assertThat(audit.hasNonNull("spanId")).isTrue();
    assertThat(audit.hasNonNull("service")).isTrue();
    assertThat(audit.hasNonNull("operation")).isTrue();
  }

  private JsonNode findAuditLine(String logs, String eventId) throws Exception {
    for (String line : logs.split("\\R")) {
      String trimmed = line.trim();
      if (!trimmed.startsWith("{") || !trimmed.contains(eventId) || !trimmed.contains("apply_transaction")) {
        continue;
      }
      JsonNode node = mapper.readTree(trimmed);
      assertThat(node.isObject()).as("audit log must be valid JSON object: %s", trimmed).isTrue();
      return node;
    }
    throw new AssertionError("no structured account audit log found for eventId=" + eventId + "\n" + logs);
  }
}
