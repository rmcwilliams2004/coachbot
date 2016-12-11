;
; Copyright (c) 2016, Courage Labs, LLC.
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

(ns coachbot.manual-coaching
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.pprint :as pprint]
            [coachbot.coaching-process :as coaching]
            [coachbot.db :as db]
            [honeysql.core :as sql]
            [honeysql.helpers :as h]
            [linked.core :as linked]
            [taoensso.timbre :as log]))

(defn list-answers
  ([max-days-back]
   (let [ds (db/datasource)
         latest-answers-query
         (-> (h/select [(sql/raw "date_format(qa.created_date, '%Y-%m-%d %h')")
                        :date]
                       [:st.id :team_id] [:scu.id :uid]
                       :scu.name
                       :bq.question
                       [:cq.question :cquestion]
                       [:qa.answer :qa])
             (h/from [:question_answers :qa])
             (h/join [:slack_coaching_users :scu]
                     [:= :scu.id :qa.slack_user_id]
                     [:slack_teams :st]
                     [:= :st.id :scu.team_id])
             (h/left-join [:base_questions :bq]
                          [:= :bq.id :qa.question_id]
                          [:custom_questions :cq]
                          [:= :cq.id :qa.cquestion_id])
             (h/where
               (sql/raw
                 (format "timestampdiff(DAY, qa.created_date, now()) < %d"
                         max-days-back)))
             (h/order-by :st.id :scu.id :qa.created_date)
             sql/format)]

     (map #(let [{:keys [date team_id uid name question cquestion qa]} %
                 q (or question cquestion)
                 ty (if cquestion "c" "b")]
             (linked/map :d date :t team_id :u uid :n name :ty ty :q q
                         :a qa))
          (jdbc/query ds latest-answers-query))))
  ([] (list-answers 5)))

(defn register-custom-question! [team-id user-id question]
  (let [ds (db/datasource)
        user-info-query
        (-> (h/select [:scu.remote_user_id :uid] [:st.team_id :tid])
            (h/from [:slack_coaching_users :scu])
            (h/join [:slack_teams :st]
                    [:= :scu.team_id :st.id])
            (h/where [:and [:= :st.id team-id] [:= :scu.id user-id]])
            sql/format)

        [{:keys [uid tid]}] (jdbc/query ds user-info-query)
        [{:keys [generated_key]}]
        (coaching/register-custom-question! tid uid question)]
    {:uid uid :tid tid :question-id generated_key}))

(defn send-custom-question-now! [team-id user-id question]
  (let [{:keys [uid tid]} (register-custom-question! team-id user-id question)]
    (coaching/next-question! tid uid uid)))

(defn delete-custom-question! [question-id]
  (jdbc/with-db-transaction
    [conn (db/datasource)]
    (first (jdbc/delete! conn :custom_questions ["id = ?" question-id]))))

(comment
  "This is the work area for coaches, for now. You'll need the following
   env variables set for your REPL pulled from 'heroku config':
     DB_TYPE, DB_URL, DB_USER, DB_PASS, SLACK_CLIENT_ID, SLACK_CLIENT_SECRET

   And DB_MAX_CONN=2
   And DB_CONN_TIMEOUT=600000"

  ;; Get rid of annoying logging
  (log/set-level! :warn)

  ;; Use this to see the last X days of answers
  (pprint/print-table (list-answers 3))

  ;; Use this to register a custom question.
  (let [team-id 1
        user-id 2
        question "What part of 'CB' would be in between valuable to focus on?"]
    (register-custom-question! team-id user-id question))

  ;; Use this to send a custom question immediately.
  (let [team-id 3
        user-id 9
        question "What has worked well for lowering your heart beat previosly"]
    (send-custom-question-now! team-id user-id question))

  ;; In case you made a mistake, you can delete a question using the ID that
  ;; the above command returned.
  (delete-custom-question! 15)

  ;; Show a bunch of data about a single slack coaching user
  (pprint/print-table
    (jdbc/query
      (db/datasource)
      [(str "select *, "
            "timestampdiff(HOUR, last_question_date, current_timestamp()) AS
            hours_since, "
            "current_timestamp as now "
            "from slack_coaching_users where email = ?")
       "travis@couragelabs.com"]))

  )
