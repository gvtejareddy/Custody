-- Create position table
CREATE TABLE IF NOT EXISTS position (
  id BIGSERIAL PRIMARY KEY,
  account_id BIGINT NOT NULL,
  asset_id VARCHAR(200) NOT NULL,
  available NUMERIC(38,18) NOT NULL DEFAULT 0,
  locked NUMERIC(38,18) NOT NULL DEFAULT 0,
  pending NUMERIC(38,18) NOT NULL DEFAULT 0,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
  UNIQUE(account_id, asset_id)
);
