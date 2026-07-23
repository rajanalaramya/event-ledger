package com.example.eventledger.account;

import com.example.eventledger.contract.v1.AccountTransactionResponse;

public record TransactionResult(AccountTransactionResponse response, boolean created, boolean replay) {}
