package com.example.eventledger.contract.v1;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountBalanceResponse(
    String accountId, String currency, BigDecimal balance, Instant updatedAt) {}
