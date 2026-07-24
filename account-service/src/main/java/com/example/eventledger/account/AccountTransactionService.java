package com.example.eventledger.account;

import com.example.eventledger.contract.v1.AccountTransactionRequest;
import com.example.eventledger.contract.v1.AccountTransactionResponse;
import com.example.eventledger.contract.v1.MoneyAmounts;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AccountTransactionService {
  // High-contention account updates can require more retries than the five normally expected.
  private static final int MAX_ATTEMPTS = 25;
  private final AccountRepository accounts;
  private final AppliedTransactionRepository transactions;
  private final BalanceCalculator calculator;
  private final Clock clock;
  private final Counter applied;
  private final Counter replays;
  private final Counter conflicts;
  private final Timer updateTimer;
  private final TransactionTemplate transactionTemplate;

  public AccountTransactionService(AccountRepository accounts, AppliedTransactionRepository transactions,
      BalanceCalculator calculator, Clock clock, MeterRegistry meters, PlatformTransactionManager transactionManager) {
    this.accounts = accounts; this.transactions = transactions; this.calculator = calculator; this.clock = clock;
    applied = meters.counter("event_ledger_transactions_applied_total");
    replays = meters.counter("event_ledger_transaction_replays_total");
    conflicts = meters.counter("event_ledger_transaction_conflicts_total");
    updateTimer = meters.timer("event_ledger_balance_update_duration_seconds");
    transactionTemplate = new TransactionTemplate(transactionManager);
  }

  public TransactionResult apply(AccountTransactionRequest request) {
    validate(request);
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      try {
        TransactionResult result = transactionTemplate.execute(status -> applyInTransaction(request));
        String outcome = result.replay() ? "REPLAYED" : "APPLIED";
        RequestAuditLog.info("apply_transaction", request.eventId(), request.accountId(), outcome);
        return result;
      } catch (ObjectOptimisticLockingFailureException ex) {
        if (attempt == MAX_ATTEMPTS) throw ex;
      } catch (DataIntegrityViolationException ex) {
        if (transactions.findByEventId(request.eventId()).isPresent()) {
          TransactionResult result = transactionTemplate.execute(status -> applyInTransaction(request));
          String outcome = result.replay() ? "REPLAYED" : "APPLIED";
          RequestAuditLog.info("apply_transaction", request.eventId(), request.accountId(), outcome);
          return result;
        }
        throw ex;
      } catch (ConflictException ex) {
        RequestAuditLog.info("apply_transaction", request.eventId(), request.accountId(), "CONFLICT");
        throw ex;
      }
    }
    throw new IllegalStateException("unreachable");
  }

  private TransactionResult applyInTransaction(AccountTransactionRequest request) {
    return updateTimer.record(() -> {
      var existing = transactions.findByEventId(request.eventId());
      if (existing.isPresent()) {
        AppliedTransactionEntity transaction = existing.get();
        if (!Objects.equals(transaction.getRequestFingerprint(), request.fingerprint())) {
          conflicts.increment();
          throw new ConflictException("eventId was already applied with a different request");
        }
        AccountEntity account = accounts.findById(transaction.getAccountId()).orElseThrow();
        replays.increment();
        return new TransactionResult(response(transaction, account.getBalance(), true), false, true);
      }
      Instant now = Instant.now(clock);
      AccountEntity account = accounts.findById(request.accountId())
          .orElseGet(() -> new AccountEntity(request.accountId(), request.currency(), BigDecimal.ZERO.setScale(2), now));
      if (!account.getCurrency().equals(request.currency())) {
        conflicts.increment();
        throw new ConflictException("account currency does not match transaction currency");
      }
      BigDecimal balance = calculator.apply(account.getBalance(), request.type(), request.amount());
      account.updateBalance(balance, now);
      accounts.save(account);
      AppliedTransactionEntity transaction = new AppliedTransactionEntity(request.eventId(), request.accountId(),
          request.type(), MoneyAmounts.normalize(request.amount()), request.currency(), request.eventTimestamp(), now,
          request.fingerprint());
      transactions.save(transaction);
      accounts.flush();
      transactions.flush();
      applied.increment();
      return new TransactionResult(response(transaction, balance, false), true, false);
    });
  }

  private AccountTransactionResponse response(AppliedTransactionEntity transaction, BigDecimal balance, boolean replay) {
    return new AccountTransactionResponse(transaction.getEventId(), transaction.getAccountId(), transaction.getType(),
        transaction.getAmount(), transaction.getCurrency(), balance, transaction.getAppliedAt(), replay);
  }

  private void validate(AccountTransactionRequest request) {
    if (request == null || blank(request.eventId()) || blank(request.accountId()) || request.type() == null
        || request.amount() == null || blank(request.currency()) || request.eventTimestamp() == null
        || blank(request.fingerprint())) throw new IllegalArgumentException("transaction request is incomplete");
    if (!request.currency().matches("[A-Z]{3}")) throw new IllegalArgumentException("currency must be ISO-4217 uppercase");
    MoneyAmounts.validatePositive(request.amount());
  }

  private boolean blank(String value) { return value == null || value.isBlank(); }
}
