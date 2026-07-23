package com.example.eventledger.contract.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class CurrencyCodesTest {
  @Test
  void acceptsUppercaseThreeLetterCodes() {
    assertThat(CurrencyCodes.validate("USD")).isEqualTo("USD");
  }

  @Test
  void rejectsInvalidCurrencyCodes() {
    assertThatIllegalArgumentException().isThrownBy(() -> CurrencyCodes.validate("usd"));
    assertThatIllegalArgumentException().isThrownBy(() -> CurrencyCodes.validate("US"));
    assertThatIllegalArgumentException().isThrownBy(() -> CurrencyCodes.validate("USDD"));
  }
}
