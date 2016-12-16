CREATE TABLE question_groups (
  id         MEDIUMINT NOT NULL         AUTO_INCREMENT,
  group_name VARCHAR(64),
  PRIMARY KEY (id)
);

CREATE TABLE bq_question_groups (
  question_id       MEDIUMINT NOT NULL,
  question_group_id MEDIUMINT NOT NULL,
  UNIQUE (question_id, question_group_id)
);

ALTER TABLE bq_question_groups
  ADD CONSTRAINT fk_bgqg_qid
FOREIGN KEY (question_id) REFERENCES base_questions (id)
  ON DELETE CASCADE;

ALTER TABLE bq_question_groups
  ADD CONSTRAINT fk_bgqg_qgid
FOREIGN KEY (question_group_id) REFERENCES question_groups (id);

INSERT INTO question_groups (group_name)
VALUES ('Planning'), ('Management'), ('Time Management'), ('Pause'),
  ('Reflection'), ('Strengths'), ('Collaboration/Teamwork'), ('Learning'),
  ('Decision Making'), ('Leadership'), ('CoachBot Feedback'), ('Habits'),
  ('Emotional Intelligence');