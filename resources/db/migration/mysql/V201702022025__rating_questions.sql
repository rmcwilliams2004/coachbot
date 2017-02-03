CREATE TABLE base_question_ratings (
  id               MEDIUMINT NOT NULL         AUTO_INCREMENT,
  base_question_id MEDIUMINT NOT NULL,
  scu_id           MEDIUMINT NOT NULL,
  rating           TINYINT,
  qa_created_date  TIMESTAMP NOT NULL,
  created_date     TIMESTAMP                  DEFAULT CURRENT_TIMESTAMP,
  updated_date     TIMESTAMP                  DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE (scu_id, qa_created_date)
);