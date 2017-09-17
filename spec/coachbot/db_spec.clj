;
; Copyright (c) 2017, Courage Labs, LLC.
;
; This file is part of CoachBot.
;
; CoachBot is free software: you can redistribute it and/or modify
; it under the terms of the GNU Affero General Public License as published by
; the Free Software Foundation, either version 3 of the License, or
; (at your option) any later version.
;
; CoachBot is distributed in the hope that it will be useful,
; but WITHOUT ANY WARRANTY; without even the implied warranty of
; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
; GNU Affero General Public License for more details.
;
; You should have received a copy of the GNU Affero General Public License
; along with CoachBot.  If not, see <http://www.gnu.org/licenses/>.
;

(ns coachbot.db-spec
  (:require [coachbot.db :as db]
            [coachbot.mocking :refer :all]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
            [speclj.core :refer :all]))

(def expected-tables
  (sorted-set "base_questions" "bq_question_groups" "channel_question_answers"
              "channel_questions" "channel_questions_asked" "custom_questions"
              "question_answers" "question_groups" "questions_asked"
              "queued_messages" "scheduled_custom_questions" "schema_version"
              "scu_question_groups" "slack_coaching_channels"
              "slack_coaching_users" "slack_teams" "user_activity"))

(defmacro should-get-tables [ds tables & transform-code]
  `(should= ~tables
            (->> ["show tables"]
                 (jdbc/query ~ds)
                 (map ~@transform-code)
                 (into (sorted-set)))))

(describe-with-level :error "Database Schema"
  (context "h2"
    (with-clean-db [ds]
      (it "should get the full schema"
        (should-get-tables @ds expected-tables
                           (comp str/lower-case :table_name)))))

  ;; Requires running MySQL database
  ;; use: 'create database coachbot default character set utf8;'
  #_(context "mysql"
    (with-all ds (db/make-db-datasource
                   "mysql"
                   (str "jdbc:mysql://localhost/coachbot?"
                        "useJDBCCompliantTimezoneShift=true&"
                        "useLegacyDatetimeCode=false&serverTimezone=UTC&"
                        "useSSL=false")
                   "root" ""))
    (it "should get the full schema"
      (should-get-tables @ds expected-tables :tables_in_coachbot))))