CREATE TABLE slack_teams (
  id               MEDIUMINT    NOT NULL         AUTO_INCREMENT,
  team_id          VARCHAR(255) NOT NULL,
  team_name        VARCHAR(255) NOT NULL,
  access_token     VARCHAR(255) NOT NULL,
  user_id          VARCHAR(255) NOT NULL,
  bot_user_id      VARCHAR(255) NOT NULL,
  bot_access_token VARCHAR(255) NOT NULL,
  created_date     TIMESTAMP                     DEFAULT CURRENT_TIMESTAMP,
  updated_date     TIMESTAMP                     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE (team_id)
);