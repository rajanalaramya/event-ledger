CREATE TABLE accounts (
    account_id VARCHAR(128) PRIMARY KEY,
    currency CHAR(3) NOT NULL,
    balance DECIMAL(19,2) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL
);

CREATE TABLE applied_transactions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id VARCHAR(128) NOT NULL UNIQUE,
    account_id VARCHAR(128) NOT NULL,
    type VARCHAR(16) NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    currency CHAR(3) NOT NULL,
    event_timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    applied_at TIMESTAMP WITH TIME ZONE NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL
);

CREATE INDEX idx_applied_transactions_account_timestamp
    ON applied_transactions (account_id, event_timestamp);
