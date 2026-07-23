package com.example.eventledger.contract.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyAmountsTest {
  @Test
  void normalizesUsingTwoDigitsAndHalfEvenRounding() {
    assertThat(MoneyAmounts.normalize(new BigDecimal("12"))).isEqualByComparingTo("12.00");
    assertThat(MoneyAmounts.normalize(new BigDecimal("1.235"))).isEqualByComparingTo("1.24");
    assertThat(MoneyAmounts.normalize(new BigDecimal("1.245"))).isEqualByComparingTo("1.24");
  }

  @Test
  void rejectsZeroAndNegativeAmounts() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> MoneyAmounts.validatePositive(BigDecimal.ZERO));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> MoneyAmounts.validatePositive(new BigDecimal("-0.01")));
  }
}
