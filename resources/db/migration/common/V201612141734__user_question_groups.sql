CREATE TABLE scu_question_groups (
  scu_id            MEDIUMINT NOT NULL,
  question_group_id MEDIUMINT NOT NULL,
  UNIQUE (scu_id, question_group_id)
);

ALTER TABLE scu_question_groups
  ADD CONSTRAINT fk_scuqg_suid
FOREIGN KEY (scu_id) REFERENCES
  slack_coaching_users (id)
  ON DELETE CASCADE;

ALTER TABLE scu_question_groups
  ADD CONSTRAINT fk_scugg_qgid
FOREIGN KEY (question_group_id)
REFERENCES question_groups (id);