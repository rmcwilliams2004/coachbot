CREATE TABLE slack_coaching_channels (
  id           MEDIUMINT NOT NULL         AUTO_INCREMENT,

  created_date TIMESTAMP                  DEFAULT CURRENT_TIMESTAMP,
  updated_date TIMESTAMP AS current_timestamp(),
  PRIMARY KEY (id)
);

CREATE TABLE channel_questions (
  id           MEDIUMINT NOT NULL         AUTO_INCREMENT,

  created_date TIMESTAMP                  DEFAULT CURRENT_TIMESTAMP,
  updated_date TIMESTAMP AS current_timestamp(),
  PRIMARY KEY (id)
);

CREATE TABLE channel_questions_asked (
  id           MEDIUMINT NOT NULL         AUTO_INCREMENT,

  created_date TIMESTAMP                  DEFAULT CURRENT_TIMESTAMP,
  updated_date TIMESTAMP AS current_timestamp(),
  PRIMARY KEY (id)
);

CREATE TABLE channel_questions_answered (
  id           MEDIUMINT NOT NULL         AUTO_INCREMENT,

  created_date TIMESTAMP                  DEFAULT CURRENT_TIMESTAMP,
  updated_date TIMESTAMP AS current_timestamp(),
  PRIMARY KEY (id)
);