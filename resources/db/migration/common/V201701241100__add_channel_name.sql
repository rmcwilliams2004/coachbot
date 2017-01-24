ALTER TABLE slack_coaching_channels
  ADD COLUMN channel_name VARCHAR(128) NOT NULL DEFAULT 'unknown';