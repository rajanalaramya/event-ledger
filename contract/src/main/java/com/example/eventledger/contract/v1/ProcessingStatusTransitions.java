package com.example.eventledger.contract.v1;

import java.util.Map;
import java.util.Set;

public final class ProcessingStatusTransitions {
  private static final Map<ProcessingStatus, Set<ProcessingStatus>> ALLOWED_TRANSITIONS =
      Map.of(
          ProcessingStatus.RECEIVED,
          Set.of(ProcessingStatus.PROCESSING, ProcessingStatus.PERMANENT_FAILURE),
          ProcessingStatus.PROCESSING,
          Set.of(
              ProcessingStatus.APPLIED,
              ProcessingStatus.RETRYABLE_FAILURE,
              ProcessingStatus.PERMANENT_FAILURE),
          ProcessingStatus.RETRYABLE_FAILURE,
          Set.of(ProcessingStatus.PROCESSING, ProcessingStatus.PERMANENT_FAILURE),
          ProcessingStatus.APPLIED,
          Set.of(),
          ProcessingStatus.PERMANENT_FAILURE,
          Set.of());

  private ProcessingStatusTransitions() {}

  public static boolean canTransition(ProcessingStatus from, ProcessingStatus to) {
    return from != null && to != null && ALLOWED_TRANSITIONS.get(from).contains(to);
  }
}
