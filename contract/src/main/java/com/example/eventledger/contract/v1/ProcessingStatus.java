package com.example.eventledger.contract.v1;

public enum ProcessingStatus {
  RECEIVED,
  PROCESSING,
  APPLIED,
  RETRYABLE_FAILURE,
  PERMANENT_FAILURE
}
