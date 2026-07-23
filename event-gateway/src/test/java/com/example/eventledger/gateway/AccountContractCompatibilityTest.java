package com.example.eventledger.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.eventledger.contract.v1.*;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AccountContractCompatibilityTest {
  @Test void accountTransactionRequestCarriesAccountContractFields() {
    EventRequest event = new EventRequest("evt-1", "acct-1", EventType.CREDIT, new BigDecimal("12.30"), "USD", Instant.parse("2025-01-01T00:00:00Z"), null);
    String fingerprint = RequestFingerprint.compute(event);
    AccountTransactionRequest request = new AccountTransactionRequest(event.eventId(), event.accountId(), event.type(), event.amount(), event.currency(), event.eventTimestamp(), fingerprint);
    assertThat(request.eventId()).isEqualTo(event.eventId());
    assertThat(request.accountId()).isEqualTo(event.accountId());
    assertThat(request.type()).isEqualTo(event.type());
    assertThat(request.amount()).isEqualByComparingTo(event.amount());
    assertThat(request.fingerprint()).isEqualTo(fingerprint);
  }
}
