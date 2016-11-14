CREATE TABLE slack_coaching_users (
  id             MEDIUMINT    NOT NULL         AUTO_INCREMENT,
  team_id        MEDIUMINT    NOT NULL,
  remote_user_id VARCHAR(64)  NOT NULL,
  email_address  VARCHAR(255) NOT NULL,
  tz             VARCHAR(128) NOT NULL,
  real_name      VARCHAR(255) NOT NULL,
  first_name     VARCHAR(128) NOT NULL,
  last_name      VARCHAR(128) NOT NULL,
  created_date   TIMESTAMP                     DEFAULT CURRENT_TIMESTAMP,
  updated_date   TIMESTAMP AS current_timestamp(),
  PRIMARY KEY (id)
)