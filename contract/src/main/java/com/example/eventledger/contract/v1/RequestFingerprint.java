package com.example.eventledger.contract.v1;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public final class RequestFingerprint {
  private RequestFingerprint() {}

  public static String compute(EventRequest request) {
    Objects.requireNonNull(request, "request must not be null");
    return compute(
        request.eventId(),
        request.accountId(),
        request.type(),
        request.amount(),
        request.currency(),
        request.eventTimestamp(),
        request.metadata());
  }

  public static String compute(
      String eventId,
      String accountId,
      EventType type,
      BigDecimal amount,
      String currency,
      Instant eventTimestamp,
      Map<String, ?> metadata) {
    String canonical =
        String.join(
            "|",
            Objects.requireNonNull(eventId, "eventId must not be null"),
            Objects.requireNonNull(accountId, "accountId must not be null"),
            Objects.requireNonNull(type, "type must not be null").name(),
            MoneyAmounts.normalize(amount).toPlainString(),
            Objects.requireNonNull(currency, "currency must not be null"),
            Instant.parse(Objects.requireNonNull(eventTimestamp, "eventTimestamp must not be null").toString())
                .toString(),
            canonicalMetadata(metadata));
    return sha256Hex(canonical);
  }

  private static String sha256Hex(String value) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(digest.length * 2);
      for (byte valueByte : digest) {
        hex.append(String.format("%02x", valueByte));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }

  private static String canonicalMetadata(Map<String, ?> metadata) {
    if (metadata == null || metadata.isEmpty()) {
      return "{}";
    }
    return canonicalObject(metadata);
  }

  private static String canonicalObject(Map<?, ?> map) {
    Map<String, Object> sorted = new TreeMap<>();
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      sorted.put(String.valueOf(entry.getKey()), entry.getValue());
    }
    StringBuilder result = new StringBuilder("{");
    boolean first = true;
    for (Map.Entry<String, Object> entry : sorted.entrySet()) {
      if (!first) {
        result.append(',');
      }
      first = false;
      result.append('"').append(escape(entry.getKey())).append("\":");
      Object value = entry.getValue();
      if (value instanceof Map<?, ?> nestedMap) {
        result.append(canonicalObject(nestedMap));
      } else {
        result.append('"').append(escape(String.valueOf(value))).append('"');
      }
    }
    return result.append('}').toString();
  }

  private static String escape(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
