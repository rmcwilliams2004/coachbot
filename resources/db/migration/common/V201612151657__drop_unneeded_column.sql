ALTER TABLE custom_questions DROP FOREIGN KEY fk_cq_st;

ALTER TABLE custom_questions
  DROP COLUMN team_id;