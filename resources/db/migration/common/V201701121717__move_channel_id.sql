-- To future generations of programmers: never do this.
-- I am a very lazy person. Be more disciplined than me!

DELETE FROM channel_question_answers;
DELETE FROM channel_questions_asked;
DELETE FROM channel_questions;
COMMIT;

ALTER TABLE channel_questions
  DROP FOREIGN KEY fk_cq_scc;

ALTER TABLE channel_questions
  DROP COLUMN channel_id;

ALTER TABLE channel_questions_asked
  ADD channel_id MEDIUMINT NOT NULL;

ALTER TABLE channel_questions_asked
  ADD CONSTRAINT fk_cqa_scc
FOREIGN KEY (channel_id) REFERENCES slack_coaching_channels (id);