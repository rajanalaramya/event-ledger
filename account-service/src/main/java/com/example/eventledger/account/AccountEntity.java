package com.example.eventledger.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "accounts")
public class AccountEntity {
  @Id
  @Column(name = "account_id", length = 128)
  private String accountId;
  @Column(nullable = false, length = 3, columnDefinition = "CHAR(3)")
  private String currency;
  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal balance;
  @Column(nullable = false)
  private Instant createdAt;
  @Column(nullable = false)
  private Instant updatedAt;
  @Version
  @Column(nullable = false)
  private long version;

  protected AccountEntity() {}

  public AccountEntity(String accountId, String currency, BigDecimal balance, Instant now) {
    this.accountId = accountId;
    this.currency = currency;
    this.balance = balance;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public String getAccountId() { return accountId; }
  public String getCurrency() { return currency; }
  public BigDecimal getBalance() { return balance; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public long getVersion() { return version; }
  public void updateBalance(BigDecimal newBalance, Instant now) { balance = newBalance; updatedAt = now; }
}
