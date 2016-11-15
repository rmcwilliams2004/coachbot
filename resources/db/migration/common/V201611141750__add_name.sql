ALTER TABLE slack_coaching_users
  ADD (name VARCHAR(64) NOT NULL);

ALTER TABLE slack_coaching_users
  CHANGE COLUMN tz timezone VARCHAR(64) NOT NULL;

ALTER TABLE slack_coaching_users
  CHANGE COLUMN email_address email VARCHAR(255) NOT NULL;