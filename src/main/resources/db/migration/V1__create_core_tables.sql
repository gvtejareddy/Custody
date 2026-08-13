-- Flyway baseline: create core tables
CREATE TABLE custody_account (
  id BIGSERIAL PRIMARY KEY,
  external_customer_id VARCHAR(255) NOT NULL UNIQUE,
  name VARCHAR(255) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE asset (
  asset_id VARCHAR(64) PRIMARY KEY,
  display_name VARCHAR(255) NOT NULL,
  network VARCHAR(128) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE wallet_mapping (
  id VARCHAR(255) PRIMARY KEY,
  account_id BIGINT NOT NULL,
  asset_id VARCHAR(64) NOT NULL,
  provider_vault_id VARCHAR(255) NOT NULL
);

CREATE TABLE ledger_entry (
  id BIGSERIAL PRIMARY KEY,
  account_id BIGINT NOT NULL,
  asset_id VARCHAR(64) NOT NULL,
  amount NUMERIC(30,10) NOT NULL,
  type VARCHAR(16) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
-- Flyway baseline migration for MVP
CREATE TABLE IF NOT EXISTS custody_account (
  id BIGSERIAL PRIMARY KEY,
  external_id VARCHAR(128) NOT NULL UNIQUE,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

CREATE TABLE IF NOT EXISTS asset (
  id BIGSERIAL PRIMARY KEY,
  symbol VARCHAR(32) NOT NULL,
  network VARCHAR(64) NOT NULL,
  enabled BOOLEAN DEFAULT true
);

CREATE TABLE IF NOT EXISTS wallet_mapping (
  id BIGSERIAL PRIMARY KEY,
  custody_account_id BIGINT NOT NULL REFERENCES custody_account(id),
  asset_id BIGINT NOT NULL REFERENCES asset(id),
  fireblocks_vault_id VARCHAR(128),
  created_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

CREATE TABLE IF NOT EXISTS ledger_entry (
  id BIGSERIAL PRIMARY KEY,
  custody_account_id BIGINT NOT NULL REFERENCES custody_account(id),
  asset_id BIGINT NOT NULL REFERENCES asset(id),
  change_amount NUMERIC(30,8) NOT NULL,
  balance NUMERIC(30,8) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);
