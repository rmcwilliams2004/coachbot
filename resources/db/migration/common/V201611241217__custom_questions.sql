-- We don't need this table now!
DROP TABLE IF EXISTS unhandled_text;

CREATE TABLE custom_questions (
  id            MEDIUMINT NOT NULL AUTO_INCREMENT,
  slack_user_id MEDIUMINT NOT NULL,
  team_id       MEDIUMINT NOT NULL,
  answered      BIT (1) NOT NULL DEFAULT 0,
  question      VARCHAR(255),
  created_date  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
) ENGINE = InnoDB;

ALTER TABLE custom_questions
  ADD CONSTRAINT fk_cq_scu FOREIGN KEY (slack_user_id)
REFERENCES slack_coaching_users (id);

ALTER TABLE custom_questions
  ADD CONSTRAINT fk_cq_st FOREIGN KEY (team_id)
REFERENCES slack_teams (id);

ALTER TABLE slack_coaching_users
  ADD asked_cqid MEDIUMINT;

ALTER TABLE slack_coaching_users
  ADD answered_cqid MEDIUMINT;

ALTER TABLE slack_coaching_users
  ADD CONSTRAINT fk_scu_cqasked
FOREIGN KEY (asked_cqid) REFERENCES custom_questions (id);

ALTER TABLE slack_coaching_users
  ADD CONSTRAINT fk_scu_cqa
FOREIGN KEY (answered_cqid) REFERENCES custom_questions (id);