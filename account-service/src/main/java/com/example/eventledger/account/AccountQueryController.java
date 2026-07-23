package com.example.eventledger.account;

import com.example.eventledger.contract.v1.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/accounts")
public class AccountQueryController {
  private final AccountRepository accounts;
  private final AppliedTransactionRepository transactions;

  public AccountQueryController(AccountRepository accounts, AppliedTransactionRepository transactions) {
    this.accounts = accounts;
    this.transactions = transactions;
  }

  @GetMapping("/{accountId}/balance")
  public AccountBalanceResponse balance(@PathVariable String accountId) {
    AccountEntity account = account(accountId);
    return new AccountBalanceResponse(account.getAccountId(), account.getCurrency(), account.getBalance(), account.getUpdatedAt());
  }

  @GetMapping("/{accountId}")
  public AccountDetailsResponse details(@PathVariable String accountId) {
    AccountEntity account = account(accountId);
    List<AccountTransactionHistoryItem> history = transactions.findByAccountIdOrderByEventTimestampAscEventIdAsc(accountId)
        .stream().map(transaction -> new AccountTransactionHistoryItem(transaction.getEventId(), transaction.getType(),
            transaction.getAmount(), transaction.getCurrency(), transaction.getEventTimestamp(), transaction.getAppliedAt()))
        .toList();
    return new AccountDetailsResponse(account.getAccountId(), account.getCurrency(), account.getBalance(),
        account.getCreatedAt(), account.getUpdatedAt(), history);
  }

  private AccountEntity account(String accountId) {
    return accounts.findById(accountId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "account not found"));
  }
}
