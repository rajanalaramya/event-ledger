package com.example.eventledger.contract.v1;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProcessingStatusTransitionsTest {
  @Test
  void allowsDefinedTransitions() {
    assertThat(
            ProcessingStatusTransitions.canTransition(
                ProcessingStatus.RECEIVED, ProcessingStatus.PROCESSING))
        .isTrue();
    assertThat(
            ProcessingStatusTransitions.canTransition(
                ProcessingStatus.PROCESSING, ProcessingStatus.APPLIED))
        .isTrue();
    assertThat(
            ProcessingStatusTransitions.canTransition(
                ProcessingStatus.RETRYABLE_FAILURE, ProcessingStatus.PERMANENT_FAILURE))
        .isTrue();
  }

  @Test
  void rejectsUndefinedAndTerminalTransitions() {
    assertThat(
            ProcessingStatusTransitions.canTransition(
                ProcessingStatus.RECEIVED, ProcessingStatus.APPLIED))
        .isFalse();
    assertThat(
            ProcessingStatusTransitions.canTransition(
                ProcessingStatus.APPLIED, ProcessingStatus.PROCESSING))
        .isFalse();
    assertThat(
            ProcessingStatusTransitions.canTransition(
                ProcessingStatus.PERMANENT_FAILURE, ProcessingStatus.PROCESSING))
        .isFalse();
  }
}
