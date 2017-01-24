ALTER TABLE channel_questions
  ADD COLUMN delivered BIT(1) NOT NULL DEFAULT 0;