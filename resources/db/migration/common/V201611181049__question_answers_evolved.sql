ALTER TABLE question_answers
  ADD created_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE TABLE questions_asked (
  id            MEDIUMINT NOT NULL AUTO_INCREMENT,
  slack_user_id MEDIUMINT NOT NULL,
  question_id   MEDIUMINT NOT NULL
);

ALTER TABLE questions_asked
  ADD CONSTRAINT fk_qasked_scu FOREIGN KEY (slack_user_id)
REFERENCES slack_coaching_users (id);

ALTER TABLE questions_asked
  ADD CONSTRAINT fk_qasked_bq FOREIGN KEY (question_id)
REFERENCES base_questions (id);

CREATE TABLE unhandled_text (
  id            MEDIUMINT NOT NULL AUTO_INCREMENT,
  slack_user_id MEDIUMINT NOT NULL,
  content       MEDIUMTEXT,
  created_date  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);

ALTER TABLE unhandled_text
  ADD CONSTRAINT fk_scu_ut FOREIGN KEY (slack_user_id)
REFERENCES slack_coaching_users (id);