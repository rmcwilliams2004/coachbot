ALTER TABLE user_activity
  ADD CONSTRAINT fk_ua_st FOREIGN KEY (team_id)
REFERENCES slack_teams (id);

ALTER TABLE user_activity
  ADD CONSTRAINT fk_ua_scu FOREIGN KEY (slack_user_id)
REFERENCES slack_coaching_users (id)
  ON DELETE CASCADE;