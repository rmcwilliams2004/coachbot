CREATE TABLE slack_coaching_channels (
  id           MEDIUMINT NOT NULL         AUTO_INCREMENT,
  team_id      MEDIUMINT NOT NULL,
  channel_id   VARCHAR(64),
  created_date TIMESTAMP                  DEFAULT CURRENT_TIMESTAMP,
  updated_date TIMESTAMP AS current_timestamp(),
  PRIMARY KEY (id),
  UNIQUE (team_id, channel_id)
);

CREATE TABLE channel_questions (
  id              MEDIUMINT NOT NULL         AUTO_INCREMENT,
  channel_id      MEDIUMINT NOT NULL,
  question        VARCHAR(255),
  left_scale_lbl  VARCHAR(64),
  right_scale_lbl VARCHAR(64),
  created_date    TIMESTAMP                  DEFAULT CURRENT_TIMESTAMP,
  updated_date    TIMESTAMP AS current_timestamp(),
  PRIMARY KEY (id)
);

CREATE TABLE channel_questions_asked (
  id                   MEDIUMINT NOT NULL         AUTO_INCREMENT,
  question_id          MEDIUMINT NOT NULL,
  expiration_timestamp TIMESTAMP NOT NULL,
  created_date         TIMESTAMP                  DEFAULT CURRENT_TIMESTAMP,
  updated_date         TIMESTAMP AS current_timestamp(),
  PRIMARY KEY (id)
);

CREATE TABLE channel_question_answers (
  id           MEDIUMINT NOT NULL         AUTO_INCREMENT,
  scu_id       MEDIUMINT NOT NULL,
  qa_id        MEDIUMINT NOT NULL,
  answer       TINYINT,
  created_date TIMESTAMP                  DEFAULT CURRENT_TIMESTAMP,
  updated_date TIMESTAMP AS current_timestamp(),
  PRIMARY KEY (id),
  UNIQUE (scu_id, qa_id)
);