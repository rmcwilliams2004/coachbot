ALTER TABLE slack_coaching_users
  ADD CONSTRAINT slack_user_team_email_uniq UNIQUE (team_id, email_address);

ALTER TABLE slack_coaching_users
  ADD CONSTRAINT fk_scu_st FOREIGN KEY (team_id) REFERENCES slack_teams (id);

ALTER TABLE question_answers
  ADD CONSTRAINT fk_qa_bq FOREIGN KEY (question_id) REFERENCES
  base_questions (id);

ALTER TABLE question_answers
  ADD CONSTRAINT fk_qa_scu FOREIGN KEY (slack_user_id) REFERENCES
  slack_coaching_users (id);

INSERT INTO base_questions (question) VALUES ('On your own personal scale of 1-10, with 10 being your ideal productivity, how productive was the last 24 hours?
What can you learn from that?');
INSERT INTO base_questions (question)
VALUES ('What are you passionate about doing today?');
INSERT INTO base_questions (question)
VALUES ('What advice would your future self give you today?');
INSERT INTO base_questions (question) VALUES (
  'If you only had 1 hour to work, and today had to be a successful day, what would you do?');
INSERT INTO base_questions (question) VALUES ('Are there things you do that don''t serve you?
If so, is there anything you can change today?');
INSERT INTO base_questions (question)
VALUES ('What new habit do you want to practice today?');
INSERT INTO base_questions (question) VALUES ('Over the last week what was the most useful question you recieved from CoachBot?
What made it useful for you?');
INSERT INTO base_questions (question) VALUES ('What will you learn today?');
INSERT INTO base_questions (question) VALUES (
  'If your workday were halved, which tasks would you eliminate from your schedule?');
INSERT INTO base_questions (question) VALUES ('What has been the most useful question that I''ve asked you so far?
What made it useful for you?');
INSERT INTO base_questions (question) VALUES ('What would make today great?');
INSERT INTO base_questions (question)
VALUES ('What could make today a slightly better day?');
INSERT INTO base_questions (question)
VALUES ('What intention do you want to have going into today?');
INSERT INTO base_questions (question) VALUES
  ('Who is important in your life that you haven''t reached out to recently?');
INSERT INTO base_questions (question) VALUES ('Over the last week what was the most useful question you recieved from CoachBot?
What made it useful for you?');
INSERT INTO base_questions (question) VALUES (
  'What can you do today that will contribute to your overall vision for you life?');
INSERT INTO base_questions (question)
VALUES ('What would your future self want you to do today?');
INSERT INTO base_questions (question)
VALUES ('What is a strength you can leverage today?');
INSERT INTO base_questions (question)
VALUES ('What can you learn from someone else today?');
INSERT INTO base_questions (question)
VALUES ('What can you teach someone today?');
INSERT INTO base_questions (question) VALUES (
  'What is the biggest problem you''re working on and how can you move that forward?');
INSERT INTO base_questions (question) VALUES (
  'What is the biggest insight you''ve had in the last week and how can you leverage that?');
INSERT INTO base_questions (question) VALUES ('Over the last week what was the most useful question you recieved from CoachBot?
What made it useful for you?');
INSERT INTO base_questions (question) VALUES ('Are you feeling reactive or proactive?
Do you want to do anything to change that?');
INSERT INTO base_questions (question)
VALUES ('How can you make your co-workers look good?');
INSERT INTO base_questions (question)
VALUES ('How can you show appreciation to someone else today?');
INSERT INTO base_questions (question)
VALUES ('What is the unique value that your teammates bring?');
INSERT INTO base_questions (question)
VALUES ('Besides money, what have you gained from work in the last month?');
INSERT INTO base_questions (question)
VALUES ('What would you do differently if you knew no one would judge you?');
INSERT INTO base_questions (question) VALUES ('Over the last week what was the most useful question you recieved from CoachBot?
What made it useful for you?');
INSERT INTO base_questions (question)
VALUES ('What are you working on now that your are excited about?');
INSERT INTO base_questions (question) VALUES (
  'What skills can you use to defer judgement just a little longer than usual today?');
INSERT INTO base_questions (question)
VALUES ('Who brings out the best work in you?');
INSERT INTO base_questions (question) VALUES (
  'What good feedback did you hear in the last 24 hours that you want to remember?');
INSERT INTO base_questions (question) VALUES (
  'What latent strength do you have that you haven''t explored or worked on yet?');
INSERT INTO base_questions (question) VALUES ('What is worth pushing for?');
INSERT INTO base_questions (question) VALUES ('What is one thing you would like to see improved at the company?
What can you do about it?');
INSERT INTO base_questions (question)
VALUES ('What can you do today that would fullfil one of your highest values?');
INSERT INTO base_questions (question) VALUES ('What has been the most useful question that you''ve been asked in the last week?
Does that tell you anything interesting?');
INSERT INTO base_questions (question) VALUES ('What''s the most challenging obstacle you face?
What is something small that you can do that could help you overcome it?');
INSERT INTO base_questions (question)
VALUES ('What skill do you want to practice today?');
INSERT INTO base_questions (question) VALUES (
  'What could you do today that would get you one step closer to finding or fulfilling your life''s purpose?');
INSERT INTO base_questions (question) VALUES (
  'Who do you want to connect with today, especially someone you haven''t connected with recently?');
INSERT INTO base_questions (question) VALUES (
  'What is the most interesting thing you have learned about yourself this month?');
INSERT INTO base_questions (question) VALUES ('What has made you the happiest in the last month?
How can you leverage that information to make you happier in the future?');
INSERT INTO base_questions (question)
VALUES ('Who can you offer praise to today?');
INSERT INTO base_questions (question) VALUES (
  'Is there information you can share with someone that could be valuablable?');
INSERT INTO base_questions (question)
VALUES ('Where is one area where you can be more proactive today?');