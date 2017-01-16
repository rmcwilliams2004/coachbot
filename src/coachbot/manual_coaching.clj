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
            [coachbot.coaching-process :as cp]
            [coachbot.channel-coaching-process :as ccp]
            [coachbot.db :as db]
            [coachbot.hsql-utils :as hu]
            [coachbot.slack :as slack]
            [coachbot.storage :as storage]
            [honeysql.core :as sql]
            [honeysql.helpers :as h]
            [linked.core :as linked]
            [taoensso.timbre :as log]))

(defn list-answers
  ([max-days-back]
   (list-answers max-days-back nil))
  ([max-days-back user-id]
   (let [ds (db/datasource)

         when-clause
         (sql/raw
           (format "timestampdiff(DAY, qa.created_date, now()) < %d"
                   max-days-back))

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
               (if user-id [:and when-clause [:= :scu.id user-id]]
                           when-clause))
             (h/order-by :st.id :scu.id :qa.created_date))]

     (map #(let [{:keys [date team_id uid name question cquestion qa]} %
                 q (or question cquestion)
                 ty (if cquestion "c" "b")]
             (linked/map :d date :t team_id :u uid :n name :ty ty :q q
                         :a qa))
          (hu/query latest-answers-query ds))))
  ([] (list-answers 5)))

(defn count-engaged
  ([days]
   "Counts the number of users that have answered a question in X Days"
   (-> (h/select (sql/raw "count(distinct scu.id) AS num_users"))
       (h/from [:slack_coaching_users :scu])
       (h/join [:question_answers :qa]
               [:= :scu.id :qa.slack_user_id])
       (h/where (sql/raw
                  (format "timestampdiff(DAY, qa.created_date, now()) < %d"
                          days)))
       (hu/query (db/datasource))
       first
       :num_users
       ))
  ([] (count-engaged 7)))

(defn register-custom-question! [team-id user-id question]
  (let [ds (db/datasource)
        user-info-query
        (-> (h/select [:scu.remote_user_id :uid] [:st.team_id :tid])
            (h/from [:slack_coaching_users :scu])
            (h/join [:slack_teams :st]
                    [:= :scu.team_id :st.id])
            (h/where [:and [:= :st.id team-id] [:= :scu.id user-id]]))

        [{:keys [uid tid]}] (hu/query user-info-query ds)
        [{:keys [generated_key]}]
        (cp/register-custom-question! tid uid question)]
    {:uid uid :tid tid :question-id generated_key}))

(defn send-custom-question-now! [team-id user-id question]
  (let [{:keys [uid tid]} (register-custom-question! team-id user-id question)]
    (cp/next-question! tid uid uid)))

(defn delete-custom-question! [question-id]
  (jdbc/with-db-transaction
    [conn (db/datasource)]
    (first (jdbc/delete! conn :custom_questions ["id = ?" question-id]))))

(defn send-message-to-active-coaching-users! [message]
  (doseq [{:keys [remote_user_id bot_access_token]}
          (-> (h/select :scu.remote_user_id :st.bot_access_token)
              (h/from [:slack_coaching_users :scu])
              (h/join [:slack_teams :st]
                      [:= :st.id :scu.team-id])
              (h/where [:= :active true])
              (hu/query (db/datasource)))]
    (slack/send-message! bot_access_token remote_user_id message)))

(defn send-question-w-buttons! [team-id user-id channel question callback-id]
  (cp/with-sending-constructs
    {:user-id user-id :team-id team-id :channel channel} [ds send-fn _]
    (send-fn question callback-id
             (map #(hash-map :name "option" :value %) (range 1 6)))))

(defn count-active []
  (-> (h/select (sql/raw "count(distinct scu.id) AS users"))
      (h/from [:slack_coaching_users :scu])
      (h/where [:= :active true])
      (hu/query (db/datasource))
      first
      :users))

(defn count-group-users []
  (-> (h/select (sql/raw "count(distinct scu_id) AS group_users"))
      (h/from :scu_question_groups)
      (hu/query (db/datasource))
      first
      :group_users))

(comment
  "This is the work area for coaches, for now. You'll need the following
   env variables set for your REPL pulled from 'heroku config':
     DB_TYPE, DB_URL, DB_USER, DB_PASS, SLACK_CLIENT_ID, SLACK_CLIENT_SECRET

   And DB_MAX_CONN=2
   And DB_CONN_TIMEOUT=600000"

  ;; Switch to manual-coaching namespace in the REPL
  (in-ns 'coachbot.manual-coaching)

  ;; Get rid of annoying logging
  (log/set-level! :warn)

  ;; Use this to see the last X days of answers
  (pprint/print-table (list-answers 7))
  (pprint/print-table (list-answers 30 18))

  ;; List all the teams
  (pprint/print-table (-> (h/select :*)
                          (h/from :slack_teams)
                          (hu/query (db/datasource))))

  ;; List all active users
  (pprint/print-table (-> (h/select [:st.id :tid] [:st.team_id :slack_team_id]
                                    [:scu.id :uid]
                                    [:scu.remote_user_id :slack_user_id]
                                    :scu.name :scu.first_name)
                          (h/from [:slack_coaching_users :scu])
                          (h/where [:= :active true])
                          (h/join [:slack_teams :st]
                                  [:= :st.id :scu.team_id])
                          (hu/query (db/datasource))))

  ;; Send a channel question
  (let [team-name "StephenCoachBotDemo"
        ds (db/datasource)

        {:keys [id team_id access_token bot_access_token]}
        (-> (h/select :*)
            (h/from :slack_teams)
            (h/where [:= team-name :team_name])
            (hu/query ds)
            first)

        channel-id (->> team_id
                        (storage/list-coaching-channels ds)
                        first)]
    (ccp/send-channel-question team_id channel-id "Some channel question"))

  ;; Count Number of engaged users
  (map count-engaged [7 14 30 60])

  ;; Count # of users using categories
  (count-group-users)
  (count-active)
  (/ (count-group-users) (count-active))

  ;; Ask a question with buttons
  (send-question-w-buttons! "T04SG55UA" "U04T4P88M" nil "Test" 1)

  ;;Common Messages to send
  (def usage-checkin
    (str
      "Just wanted to check in.  I noticed that you haven't had "
      "a chance to use CoachBot much recently.  I was just curious if "
      "you have just been busy in the new year or if something else is "
      "annoying you about coachbot"))

  (def oops
    (str
      "Oops.  I wasn't reading the dates quite right.  I'm still curious "
      "what you think of CoachBot so far though"))

  ;; Use this to register a custom question.
  (let [team-id 1
        user-id 2
        question "What part of 'CB' would be in between valuable to focus on?"]
    (register-custom-question! team-id user-id question))

  ;; Use this to send a custom question immediately.
  (let [team-id 3
        user-id 18
        question oops]
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
