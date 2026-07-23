package com.example.eventledger.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.eventledger.contract.v1.AccountTransactionRequest;
import com.example.eventledger.contract.v1.EventType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ConcurrentAccountUpdatesTest {
  private final AccountTransactionService service;
  private final AccountRepository accounts;
  private final AppliedTransactionRepository transactions;

  @Autowired
  ConcurrentAccountUpdatesTest(AccountTransactionService service, AccountRepository accounts,
      AppliedTransactionRepository transactions) {
    this.service = service;
    this.accounts = accounts;
    this.transactions = transactions;
  }

  @BeforeEach
  void clear() {
    transactions.deleteAll();
    accounts.deleteAll();
  }

  @Test
  void appliesConcurrentCreditsAndDebitsWithExactBalance() throws Exception {
    String accountId = "concurrent-account";
    service.apply(request("seed", accountId, EventType.CREDIT, "1.00", "seed"));
    List<AccountTransactionRequest> requests = new ArrayList<>();
    for (int i = 0; i < 50; i++) requests.add(request("credit-" + i, accountId, EventType.CREDIT, "1.00", "c-" + i));
    for (int i = 0; i < 20; i++) requests.add(request("debit-" + i, accountId, EventType.DEBIT, "1.00", "d-" + i));

    runConcurrently(requests);

    assertThat(accounts.findById(accountId).orElseThrow().getBalance()).isEqualByComparingTo("31.00");
    assertThat(transactions.count()).isEqualTo(71);
  }

  @Test
  void appliesConcurrentDuplicateEventOnlyOnce() throws Exception {
    String accountId = "duplicate-account";
    service.apply(request("seed", accountId, EventType.CREDIT, "1.00", "seed"));
    AccountTransactionRequest duplicate = request("duplicate-event", accountId, EventType.CREDIT, "2.00", "shared");
    runConcurrently(java.util.Collections.nCopies(24, duplicate));

    assertThat(accounts.findById(accountId).orElseThrow().getBalance()).isEqualByComparingTo("3.00");
    assertThat(transactions.findByEventId("duplicate-event")).isPresent();
    assertThat(transactions.count()).isEqualTo(2);
  }

  private void runConcurrently(List<AccountTransactionRequest> requests) throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(requests.size());
    CountDownLatch ready = new CountDownLatch(requests.size());
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<TransactionResult>> futures = new ArrayList<>();
      for (AccountTransactionRequest request : requests) {
        futures.add(executor.submit(() -> {
          ready.countDown();
          if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("workers did not start");
          return service.apply(request);
        }));
      }
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      for (Future<TransactionResult> future : futures) future.get(20, TimeUnit.SECONDS);
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }
  }

  private AccountTransactionRequest request(String eventId, String accountId, EventType type, String amount,
      String fingerprint) {
    return new AccountTransactionRequest(eventId, accountId, type, new BigDecimal(amount), "USD",
        Instant.parse("2025-01-01T00:00:00Z"), fingerprint);
  }
}
