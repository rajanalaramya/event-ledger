package com.example.eventledger.account;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountServiceIntegrationTest {
  private final MockMvc mvc;
  private final AccountRepository accounts;
  private final AppliedTransactionRepository transactions;

  @Autowired
  AccountServiceIntegrationTest(MockMvc mvc, AccountRepository accounts, AppliedTransactionRepository transactions) {
    this.mvc = mvc;
    this.accounts = accounts;
    this.transactions = transactions;
  }

  @BeforeEach
  void clear() {
    transactions.deleteAll();
    accounts.deleteAll();
  }

  @Test
  void appliesTransactionsReplaysIdenticalEventsAndReportsConflicts() throws Exception {
    String credit = request("event-1", "CREDIT", "10.00", "fingerprint-1");
    mvc.perform(post("/accounts/a-1/transactions").contentType(MediaType.APPLICATION_JSON).content(credit))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.balanceAfter").value(10));
    mvc.perform(post("/accounts/a-1/transactions").contentType(MediaType.APPLICATION_JSON).content(credit))
        .andExpect(status().isOk()).andExpect(header().string("Idempotent-Replay", "true"));
    mvc.perform(post("/accounts/a-1/transactions").contentType(MediaType.APPLICATION_JSON)
            .content(request("event-1", "CREDIT", "11.00", "fingerprint-2")))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("CONFLICT"));
    mvc.perform(post("/accounts/a-1/transactions").contentType(MediaType.APPLICATION_JSON)
            .content(request("event-2", "DEBIT", "3.00", "fingerprint-3")))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.balanceAfter").value(7));
    mvc.perform(get("/accounts/a-1")).andExpect(status().isOk())
        .andExpect(jsonPath("$.transactions.length()").value(2));
    mvc.perform(get("/accounts/a-1/balance")).andExpect(status().isOk())
        .andExpect(jsonPath("$.balance").value(7));
  }

  @Test
  void rejectsCurrencyMismatchWithoutPartialTransaction() throws Exception {
    mvc.perform(post("/accounts/a-2/transactions").contentType(MediaType.APPLICATION_JSON)
        .content(request("currency-1", "CREDIT", "10.00", "fingerprint-1"))).andExpect(status().isCreated());
    mvc.perform(post("/accounts/a-2/transactions").contentType(MediaType.APPLICATION_JSON)
        .content(request("currency-2", "CREDIT", "10.00", "fingerprint-2").replace("\"USD\"", "\"EUR\"")))
        .andExpect(status().isConflict());
    mvc.perform(get("/accounts/a-2/balance")).andExpect(jsonPath("$.balance").value(10));
    org.assertj.core.api.Assertions.assertThat(transactions.count()).isEqualTo(1);
  }

  @Test
  void ordersHistoryByEventTimestampThenEventId() throws Exception {
    mvc.perform(post("/accounts/a-1/transactions").contentType(MediaType.APPLICATION_JSON)
        .content(requestAt("late", "CREDIT", "1.00", "late", "2025-01-03T00:00:00Z"))).andExpect(status().isCreated());
    mvc.perform(post("/accounts/a-1/transactions").contentType(MediaType.APPLICATION_JSON)
        .content(requestAt("same-b", "CREDIT", "1.00", "same-b", "2025-01-01T00:00:00Z"))).andExpect(status().isCreated());
    mvc.perform(post("/accounts/a-1/transactions").contentType(MediaType.APPLICATION_JSON)
        .content(requestAt("same-a", "CREDIT", "1.00", "same-a", "2025-01-01T00:00:00Z"))).andExpect(status().isCreated());

    mvc.perform(get("/accounts/a-1")).andExpect(status().isOk())
        .andExpect(jsonPath("$.transactions[0].eventId").value("same-a"))
        .andExpect(jsonPath("$.transactions[1].eventId").value("same-b"))
        .andExpect(jsonPath("$.transactions[2].eventId").value("late"));
  }

  @Test
  void healthEndpointIsAvailable() throws Exception {
    mvc.perform(get("/health")).andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"))
        .andExpect(jsonPath("$.service").value("account-service"))
        .andExpect(jsonPath("$.database.status").value("UP"));
  }

  private String request(String eventId, String type, String amount, String fingerprint) {
    return requestAt(eventId, type, amount, fingerprint, "2025-01-01T00:00:00Z");
  }

  private String requestAt(String eventId, String type, String amount, String fingerprint, String timestamp) {
    return ("{\"eventId\":\"%s\",\"accountId\":\"a-%s\",\"type\":\"%s\",\"amount\":%s,"
        + "\"currency\":\"USD\",\"eventTimestamp\":\"%s\",\"fingerprint\":\"%s\"}")
        .formatted(eventId, eventId.startsWith("currency") ? "2" : "1", type, amount, timestamp, fingerprint);
  }
}
