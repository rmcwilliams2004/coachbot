ALTER TABLE slack_coaching_users
  DROP FOREIGN KEY fk_scu_cqa;

ALTER TABLE slack_coaching_users
  DROP COLUMN answered_cqid;

ALTER TABLE questions_asked
  ADD cquestion_id MEDIUMINT;

ALTER TABLE questions_asked
  ADD CONSTRAINT fk_qasked_cq FOREIGN KEY (cquestion_id)
REFERENCES custom_questions (id);

ALTER TABLE question_answers
  ADD cquestion_id MEDIUMINT;

ALTER TABLE question_answers
  ADD CONSTRAINT fk_qa_cq FOREIGN KEY (cquestion_id) REFERENCES
  custom_questions (id);
