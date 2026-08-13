-- Create provider_event table
CREATE TABLE IF NOT EXISTS provider_event (
  id BIGSERIAL PRIMARY KEY,
  provider VARCHAR(100) NOT NULL,
  provider_event_id VARCHAR(255) NOT NULL,
  event_type VARCHAR(200),
  payload JSONB,
  received_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_provider_event_provider_event_id ON provider_event(provider, provider_event_id);

-- Create outbox table
CREATE TABLE IF NOT EXISTS outbox_event (
  id BIGSERIAL PRIMARY KEY,
  aggregate_type VARCHAR(200),
  aggregate_id VARCHAR(200),
  type VARCHAR(200),
  payload JSONB,
  processed BOOLEAN DEFAULT false,
  attempts INTEGER DEFAULT 0,
  next_attempt_at TIMESTAMP WITH TIME ZONE,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);
