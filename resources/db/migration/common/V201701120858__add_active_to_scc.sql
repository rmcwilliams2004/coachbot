ALTER TABLE slack_coaching_channels
  ADD COLUMN
  active BIT(1) NOT NULL DEFAULT 1;