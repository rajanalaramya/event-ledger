package com.example.eventledger.gateway;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import org.slf4j.MDC;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(DownstreamPermanentException.class)
  ProblemDetail permanent(DownstreamPermanentException e, HttpServletRequest request) {
    return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Permanent failure", e.getMessage(), "PERMANENT_FAILURE", request);
  }
  @ExceptionHandler(ConflictException.class)
  ProblemDetail conflict(ConflictException e, HttpServletRequest request) { return problem(HttpStatus.CONFLICT, "Conflict", e.getMessage(), "CONFLICT", request); }
  @ExceptionHandler(DownstreamUnavailableException.class)
  ResponseEntity<ProblemDetail> unavailable(DownstreamUnavailableException e, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header(HttpHeaders.RETRY_AFTER, "1")
        .body(problem(HttpStatus.SERVICE_UNAVAILABLE, "Downstream unavailable", e.getMessage(), "DOWNSTREAM_UNAVAILABLE", request));
  }
  @ExceptionHandler(ProcessingInProgressException.class)
  ResponseEntity<ProblemDetail> processing(ProcessingInProgressException e, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header(HttpHeaders.RETRY_AFTER, "1")
        .body(problem(HttpStatus.SERVICE_UNAVAILABLE, "Processing in progress", e.getMessage(), "PROCESSING_IN_PROGRESS", request));
  }
  @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
  ProblemDetail invalid(Exception e, HttpServletRequest request) { return problem(HttpStatus.BAD_REQUEST, "Validation failed", "request is invalid", "VALIDATION_ERROR", request); }
  @ExceptionHandler(MethodArgumentNotValidException.class)
  ProblemDetail validation(MethodArgumentNotValidException e, HttpServletRequest request) {
    ProblemDetail p = problem(HttpStatus.BAD_REQUEST, "Validation failed", "request validation failed", "VALIDATION_ERROR", request);
    Map<String, String> fields = new LinkedHashMap<>();
    for (FieldError error : e.getBindingResult().getFieldErrors()) fields.put(error.getField(), error.getDefaultMessage());
    p.setProperty("errors", fields); return p;
  }
  @ExceptionHandler(ResponseStatusException.class)
  ProblemDetail status(ResponseStatusException e, HttpServletRequest request) { return problem(HttpStatus.valueOf(e.getStatusCode().value()), "Request failed", Optional.ofNullable(e.getReason()).orElse("request failed"), "NOT_FOUND", request); }
  private ProblemDetail problem(HttpStatus status, String title, String detail, String code, HttpServletRequest request) {
    ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail); p.setTitle(title); p.setType(URI.create("urn:event-ledger:" + code.toLowerCase(Locale.ROOT)));
    p.setInstance(URI.create(request.getRequestURI())); p.setProperty("errorCode", code); p.setProperty("traceId", Optional.ofNullable(MDC.get("traceId")).orElse(""));
    p.setProperty("timestamp", Instant.now().toString()); return p;
  }
}
