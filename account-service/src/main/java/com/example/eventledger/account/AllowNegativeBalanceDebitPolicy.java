package com.example.eventledger.account;

import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class AllowNegativeBalanceDebitPolicy implements DebitPolicy {
  @Override
  public void validate(BigDecimal currentBalance, BigDecimal debitAmount) {
    // The ledger's default policy deliberately permits overdrafts.
  }
}
