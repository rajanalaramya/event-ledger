package com.example.eventledger.contract.v1;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record EventRequest(
    @NotBlank @Size(max = 128) String eventId,
    @NotBlank @Size(max = 128) String accountId,
    @NotNull EventType type,
    @NotNull @DecimalMin(value = "0.00", inclusive = false) BigDecimal amount,
    @NotBlank String currency,
    @NotNull Instant eventTimestamp,
    @Size(max = 32) Map<String, ?> metadata) {

  public EventRequest {
    amount = MoneyAmounts.validatePositive(amount);
    currency = CurrencyCodes.validate(currency);
    metadata =
        metadata == null
            ? null
            : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
  }

  @AssertTrue(message = "metadata keys must be at most 64 characters and string values at most 256 characters")
  public boolean isMetadataValid() {
    if (metadata == null) {
      return true;
    }
    return metadata.entrySet().stream()
        .allMatch(
            entry ->
                entry.getKey() != null
                    && entry.getKey().length() <= 64
                    && (!(entry.getValue() instanceof String value) || value.length() <= 256));
  }
}
