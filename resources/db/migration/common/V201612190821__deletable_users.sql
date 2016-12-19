ALTER TABLE questions_asked
  DROP FOREIGN KEY fk_qasked_scu;

ALTER TABLE questions_asked
  DROP FOREIGN KEY fk_qasked_cq;

ALTER TABLE question_answers
  DROP FOREIGN KEY fk_qa_scu;

ALTER TABLE question_answers
  DROP FOREIGN KEY fk_qa_cq;

ALTER TABLE custom_questions
  DROP FOREIGN KEY fk_cq_scu;

ALTER TABLE questions_asked
  ADD CONSTRAINT fk_qasked_scu FOREIGN KEY (slack_user_id)
REFERENCES slack_coaching_users (id)
  ON DELETE CASCADE;

ALTER TABLE questions_asked
  ADD CONSTRAINT fk_qasked_cq FOREIGN KEY (cquestion_id)
REFERENCES custom_questions (id)
  ON DELETE CASCADE;

ALTER TABLE question_answers
  ADD CONSTRAINT fk_qa_scu FOREIGN KEY (slack_user_id)
REFERENCES slack_coaching_users (id)
  ON DELETE CASCADE;

ALTER TABLE question_answers
  ADD CONSTRAINT fk_qa_cq FOREIGN KEY (cquestion_id)
REFERENCES custom_questions (id)
  ON DELETE CASCADE;

ALTER TABLE custom_questions
  ADD CONSTRAINT fk_cq_scu FOREIGN KEY (slack_user_id)
REFERENCES slack_coaching_users (id)
  ON DELETE CASCADE;