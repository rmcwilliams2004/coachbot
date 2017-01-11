ALTER TABLE user_activity
  MODIFY COLUMN raw_msg TEXT;

ALTER TABLE user_activity
  MODIFY COLUMN processed_msg TEXT;

ALTER TABLE user_activity
  MODIFY COLUMN event_text TEXT;

-- consistency, really
ALTER TABLE question_answers
  MODIFY COLUMN answer TEXT;