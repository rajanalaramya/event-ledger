package com.example.eventledger.account;

import com.example.eventledger.contract.v1.AccountTransactionRequest;
import com.example.eventledger.contract.v1.AccountTransactionResponse;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/accounts/{accountId}/transactions")
public class AccountTransactionController {
  private final AccountTransactionService service;

  public AccountTransactionController(AccountTransactionService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<AccountTransactionResponse> apply(@PathVariable String accountId,
      @RequestBody AccountTransactionRequest request) {
    if (!accountId.equals(request.accountId())) throw new IllegalArgumentException("path accountId must match body accountId");
    TransactionResult result = service.apply(request);
    if (result.replay()) return ResponseEntity.ok().header("Idempotent-Replay", "true").body(result.response());
    return ResponseEntity.created(URI.create("/accounts/" + accountId + "/transactions/" + request.eventId()))
        .body(result.response());
  }
}
