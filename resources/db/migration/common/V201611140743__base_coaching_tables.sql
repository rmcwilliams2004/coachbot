ALTER TABLE slack_coaching_users
  ADD CONSTRAINT slack_user_team_email_uniq UNIQUE (team_id, email_address);

ALTER TABLE slack_coaching_users
  ADD CONSTRAINT fk_scu_st FOREIGN KEY (team_id) REFERENCES slack_teams (id);