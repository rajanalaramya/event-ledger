package com.example.eventledger.contract.v1;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AccountDetailsResponse(
    String accountId,
    String currency,
    BigDecimal balance,
    Instant createdAt,
    Instant updatedAt,
    List<AccountTransactionHistoryItem> transactions) {}
