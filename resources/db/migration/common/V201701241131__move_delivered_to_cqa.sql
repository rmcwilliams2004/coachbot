ALTER TABLE channel_questions
  DROP COLUMN delivered;

ALTER TABLE channel_questions_asked
  ADD COLUMN delivered BIT(1) NOT NULL DEFAULT 0;

