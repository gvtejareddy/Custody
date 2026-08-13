-- Create custody_transaction table if it does not exist
CREATE TABLE IF NOT EXISTS custody_transaction (
  id BIGSERIAL PRIMARY KEY,
  custody_account_id BIGINT NOT NULL,
  asset_id VARCHAR(64) NOT NULL,
  network VARCHAR(64),
  type VARCHAR(40) NOT NULL,
  status VARCHAR(50) NOT NULL,
  amount NUMERIC(38,18) NOT NULL,
  destination_address VARCHAR(500),
  provider_transaction_id VARCHAR(200),
  idempotency_key VARCHAR(200) UNIQUE,
  created_at TIMESTAMPTZ DEFAULT now(),
  updated_at TIMESTAMPTZ DEFAULT now()
);
