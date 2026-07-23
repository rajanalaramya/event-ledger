package com.example.eventledger.account;

import com.example.eventledger.contract.v1.EventType;
import com.example.eventledger.contract.v1.MoneyAmounts;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class BalanceCalculator {
  private final DebitPolicy debitPolicy;

  public BalanceCalculator(DebitPolicy debitPolicy) {
    this.debitPolicy = debitPolicy;
  }

  public BigDecimal apply(BigDecimal balance, EventType type, BigDecimal amount) {
    BigDecimal normalizedBalance = MoneyAmounts.normalize(balance);
    BigDecimal normalizedAmount = MoneyAmounts.validatePositive(amount);
    if (type == EventType.CREDIT) return MoneyAmounts.normalize(normalizedBalance.add(normalizedAmount));
    debitPolicy.validate(normalizedBalance, normalizedAmount);
    return MoneyAmounts.normalize(normalizedBalance.subtract(normalizedAmount));
  }
}
