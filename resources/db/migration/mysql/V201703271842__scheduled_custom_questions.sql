CREATE TABLE scheduled_custom_questions (
  id             MEDIUMINT   NOT NULL AUTO_INCREMENT,
  slack_user_id  MEDIUMINT   NOT NULL,
  schedule       VARCHAR(32) NOT NULL,
  question       VARCHAR(255),
  active         BIT(1)               DEFAULT 1,
  created_date   TIMESTAMP            DEFAULT CURRENT_TIMESTAMP,
  updated_date   TIMESTAMP            DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  last_sent_date TIMESTAMP   NOT NULL,

  PRIMARY KEY (id)
)