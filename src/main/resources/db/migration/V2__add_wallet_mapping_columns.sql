-- Add provider wallet and blockchain address columns to wallet_mapping
ALTER TABLE wallet_mapping
  ADD COLUMN IF NOT EXISTS provider_wallet_id VARCHAR(255),
  ADD COLUMN IF NOT EXISTS blockchain_address VARCHAR(500);
