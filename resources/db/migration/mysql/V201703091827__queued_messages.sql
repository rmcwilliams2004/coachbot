CREATE TABLE queued_messages (
  id            MEDIUMINT NOT NULL AUTO_INCREMENT,
  team_id       MEDIUMINT NOT NULL,
  channel_id    MEDIUMINT NOT NULL,
  raw_msg       MEDIUMTEXT,
  delivery_date TIMESTAMP,
  delivered     BIT (1)                    DEFAULT 0,
  created_date  TIMESTAMP                  DEFAULT CURRENT_TIMESTAMP,
  updated_date  TIMESTAMP                  DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
)