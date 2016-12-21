ALTER TABLE slack_coaching_channels
  ADD CONSTRAINT fk_scc_st FOREIGN KEY (team_id)
REFERENCES slack_teams (id);

ALTER TABLE channel_questions
  ADD CONSTRAINT fk_cq_scc FOREIGN KEY (channel_id)
REFERENCES slack_coaching_channels (id);

ALTER TABLE channel_questions_asked
  ADD CONSTRAINT fk_cqasked_cq FOREIGN KEY (question_id)
REFERENCES channel_questions (id);

ALTER TABLE channel_question_answers
  ADD CONSTRAINT fk_cqa_cqasked FOREIGN KEY (qa_id)
REFERENCES channel_questions_asked (id);

ALTER TABLE channel_question_answers
  ADD CONSTRAINT fk_cqa_scu FOREIGN KEY (scu_id)
REFERENCES slack_coaching_users (id)
  ON DELETE CASCADE;