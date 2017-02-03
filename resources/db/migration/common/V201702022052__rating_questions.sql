ALTER TABLE base_question_ratings
  ADD CONSTRAINT fk_base_question_id FOREIGN KEY (base_question_id)
REFERENCES base_question (id);