package com.example.eventledger.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.eventledger.contract.v1.EventType;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class BalanceCalculatorTest {
  private final BalanceCalculator calculator = new BalanceCalculator(new AllowNegativeBalanceDebitPolicy());

  @Test
  void creditsAndDebitsNormalizeToMoneyScale() {
    assertThat(calculator.apply(new BigDecimal("10"), EventType.CREDIT, new BigDecimal("1.005")))
        .isEqualByComparingTo("11.00");
    assertThat(calculator.apply(new BigDecimal("10.00"), EventType.DEBIT, new BigDecimal("12")))
        .isEqualByComparingTo("-2.00");
  }
}
