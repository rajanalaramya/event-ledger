package com.example.eventledger.account;

import java.math.BigDecimal;

public interface DebitPolicy {
  void validate(BigDecimal currentBalance, BigDecimal debitAmount);
}
