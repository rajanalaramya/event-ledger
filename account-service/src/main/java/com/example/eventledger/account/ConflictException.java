package com.example.eventledger.account;

public class ConflictException extends RuntimeException {
  public ConflictException(String message) {
    super(message);
  }
}
