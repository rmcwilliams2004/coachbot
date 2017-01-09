CREATE TABLE user_activity (
  id            MEDIUMINT NOT NULL         AUTO_INCREMENT,
  team_id       MEDIUMINT NOT NULL,
  slack_user_id MEDIUMINT NOT NULL,
  raw_msg       MEDIUMTEXT,
  processed_msg MEDIUMTEXT,
  mtype         VARCHAR(10),
  channel_id    VARCHAR(64),
  event_text    VARCHAR(128),
  cb_id         VARCHAR(32),
  cb_aname      VARCHAR(32),
  cb_aval       VARCHAR(8),
  created_date  TIMESTAMP                  DEFAULT CURRENT_TIMESTAMP,
  updated_date  TIMESTAMP AS current_timestamp(),
  PRIMARY KEY (id)
);