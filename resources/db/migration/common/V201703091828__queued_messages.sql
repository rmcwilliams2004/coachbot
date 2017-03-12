ALTER TABLE queued_messages
  ADD CONSTRAINT fk_qm_st FOREIGN KEY (team_id)
REFERENCES slack_teams (id);

ALTER TABLE queued_messages
  ADD CONSTRAINT fk_qm_scc FOREIGN KEY (channel_id)
REFERENCES slack_coaching_channels (id);