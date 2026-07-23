package com.example.eventledger.contract.v1;

import java.util.Objects;
import java.util.regex.Pattern;

public final class CurrencyCodes {
  private static final Pattern ISO_4217_SHAPE = Pattern.compile("[A-Z]{3}");

  private CurrencyCodes() {}

  public static String validate(String currency) {
    Objects.requireNonNull(currency, "currency must not be null");
    if (!ISO_4217_SHAPE.matcher(currency).matches()) {
      throw new IllegalArgumentException("currency must be an uppercase three-letter code");
    }
    return currency;
  }
}
