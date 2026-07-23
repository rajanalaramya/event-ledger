package com.example.eventledger.contract.v1;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RequestFingerprintTest {
  @Test
  void producesTheSameFingerprintForEquivalentRequests() {
    Map<String, Object> firstMetadata = new LinkedHashMap<>();
    firstMetadata.put("source", "mobile");
    firstMetadata.put("context", Map.of("region", "us", "channel", "app"));

    Map<String, Object> secondMetadata = new LinkedHashMap<>();
    secondMetadata.put("context", Map.of("channel", "app", "region", "us"));
    secondMetadata.put("source", "mobile");

    String first =
        RequestFingerprint.compute(
            "event-1",
            "account-1",
            EventType.CREDIT,
            new BigDecimal("10.0"),
            "USD",
            Instant.parse("2026-07-23T12:00:00Z"),
            firstMetadata);
    String second =
        RequestFingerprint.compute(
            "event-1",
            "account-1",
            EventType.CREDIT,
            new BigDecimal("10.00"),
            "USD",
            Instant.parse("2026-07-23T12:00:00Z"),
            secondMetadata);

    assertThat(first).isEqualTo(second).hasSize(64);
  }

  @Test
  void producesDifferentFingerprintsForDifferentPayloads() {
    String first =
        RequestFingerprint.compute(
            "event-1",
            "account-1",
            EventType.CREDIT,
            new BigDecimal("10.00"),
            "USD",
            Instant.parse("2026-07-23T12:00:00Z"),
            null);
    String second =
        RequestFingerprint.compute(
            "event-1",
            "account-1",
            EventType.DEBIT,
            new BigDecimal("10.00"),
            "USD",
            Instant.parse("2026-07-23T12:00:00Z"),
            null);

    assertThat(first).isNotEqualTo(second);
  }
}
