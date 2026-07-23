package com.example.eventledger.contract.v1;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public final class MoneyAmounts {
  public static final int SCALE = 2;
  public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_EVEN;

  private MoneyAmounts() {}

  public static BigDecimal normalize(BigDecimal amount) {
    return Objects.requireNonNull(amount, "amount must not be null").setScale(SCALE, ROUNDING_MODE);
  }

  public static BigDecimal validatePositive(BigDecimal amount) {
    BigDecimal normalized = normalize(amount);
    if (normalized.signum() <= 0) {
      throw new IllegalArgumentException("amount must be positive");
    }
    return normalized;
  }
}
