package com.example.eventledger.gateway;

import java.util.function.Predicate;

/** Limits automatic account-service retries to failures that can safely be transient. */
public final class AccountServiceRetryPredicate implements Predicate<Throwable> {
  @Override
  public boolean test(Throwable error) {
    return RetryClassifier.isRetryable(error);
  }
}
