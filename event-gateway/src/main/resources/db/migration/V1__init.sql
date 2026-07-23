CREATE TABLE events (
  event_id VARCHAR(128) PRIMARY KEY,
  account_id VARCHAR(128) NOT NULL,
  event_type VARCHAR(16) NOT NULL,
  amount DECIMAL(19,2) NOT NULL,
  currency VARCHAR(3) NOT NULL,
  event_timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
  received_at TIMESTAMP WITH TIME ZONE NOT NULL,
  request_fingerprint VARCHAR(64) NOT NULL,
  metadata_json CLOB,
  processing_status VARCHAR(32) NOT NULL,
  attempt_count INT NOT NULL,
  last_attempt_at TIMESTAMP WITH TIME ZONE,
  applied_at TIMESTAMP WITH TIME ZONE,
  last_error_code VARCHAR(64),
  last_error_message VARCHAR(512),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  version BIGINT NOT NULL
);
CREATE INDEX idx_events_account_timestamp ON events (account_id, event_timestamp, event_id);
CREATE INDEX idx_events_status ON events (processing_status);
