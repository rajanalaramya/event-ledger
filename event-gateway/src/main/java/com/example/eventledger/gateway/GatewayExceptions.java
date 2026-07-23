package com.example.eventledger.gateway;

class ConflictException extends RuntimeException { ConflictException(String message) { super(message); } }
class DownstreamUnavailableException extends RuntimeException { DownstreamUnavailableException(String message, Throwable cause) { super(message, cause); } }
class ProcessingInProgressException extends RuntimeException { ProcessingInProgressException() { super("event is currently processing"); } }
class DownstreamPermanentException extends RuntimeException {
  DownstreamPermanentException(String message) { super(message); }
}
