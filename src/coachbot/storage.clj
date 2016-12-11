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

(ns coachbot.storage
  (:require [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :as cske]
            [clj-time.jdbc]
            [clojure.java.jdbc :as jdbc]
            [clojure.set :as set]
            [coachbot.db :as db]
            [coachbot.env :as env]
            [honeysql.core :as sql]
            [honeysql.helpers :as h]
            [taoensso.timbre :as log]))

(defn get-access-tokens [ds team-id]
  (let [query (-> (h/select [:access_token "access_token"]
                            [:bot_access_token "bot_access_token"])
                  (h/from :slack_teams)
                  (h/where [:= :team_id team-id])
                  sql/format)
        [{:keys [access_token bot_access_token]}] (jdbc/query ds query)]
    [access_token bot_access_token]))

(defn get-bot-user-id [ds team-id]
  (let [query (-> (h/select [:bot_user_id "bot_user_id"])
                  (h/from :slack_teams)
                  (h/where [:= :team_id team-id])
                  sql/format)
        [{:keys [bot_user_id]}] (jdbc/query ds query)]
    (log/debugf "Bot user ID: %s" bot_user_id)
    bot_user_id))

(defn is-bot-user? [ds team-id user]
  (= (get-bot-user-id ds team-id) user))

(defn store-slack-auth! [ds {:keys [team-id] :as auth-data}]
  (jdbc/with-db-transaction
    [conn ds]
    (let [new-record (cske/transform-keys csk/->snake_case
                                          auth-data)
          [a _] (get-access-tokens ds team-id)]
      (if a
        (jdbc/update! conn :slack_teams new-record ["team_id = ?" team-id])
        (jdbc/insert! conn :slack_teams new-record)))))

(defn- get-team-id [ds team-id]
  (let [team-id-query (-> (h/select :id)
                          (h/from :slack_teams)
                          (h/where [:= :team_id team-id])
                          sql/format)
        [{team-id :id}] (jdbc/query ds team-id-query)]
    team-id))

(defn- coaching-users-query [team-internal-id & [user-email]]
  (let [where-clause [:and
                      [:= :team_id team-internal-id]]
        where-clause (if user-email
                       (conj where-clause [:= :email user-email])
                       (conj where-clause [:= :active 1]))]
    (-> (h/select
          :*,
          [(sql/raw
             "timestampdiff(HOUR, last_question_date, current_timestamp())")
           :hours-since-question])
        (h/from :slack_coaching_users)
        (h/where where-clause)
        sql/format)))

(defn- get-coaching-user-raw [ds team-id user-email]
  (let [team-internal-id (get-team-id ds team-id)
        query (coaching-users-query team-internal-id user-email)
        [result] (jdbc/query ds query)]
    result))

(defn- convert-user [team-id user]
  (when user
    (as-> user x
          (cske/transform-keys csk/->kebab-case x)
          (dissoc x :created-date :updated-date :last-question-date :id
                  :team-id)
          (assoc x :team-id team-id)
          (set/rename-keys x {:remote-user-id :id}))))

(defn get-coaching-user [ds team-id user-email]
  (convert-user team-id (get-coaching-user-raw ds team-id user-email)))

(defn add-coaching-user! [ds {:keys [email team-id coaching-time] :as user}]
  (jdbc/with-db-transaction
    [conn ds]
    (let [existing-record (get-coaching-user conn team-id email)
          team-id (get-team-id ds team-id)
          new-record (as-> user x
                           (cske/transform-keys csk/->snake_case x)
                           (assoc x :team_id team-id)
                           (set/rename-keys x {:id :remote_user_id}))]
      (if existing-record
        (do (jdbc/update! conn :slack_coaching_users
                          {:active 1}
                          ["email = ? AND team_id = ?" email team-id])
            false)
        (do (jdbc/insert! conn :slack_coaching_users new-record) true)))))

(defn remove-coaching-user! [ds {:keys [email team-id]}]
  (jdbc/with-db-transaction
    [conn ds]
    (let [team-id (get-team-id ds team-id)]
      (jdbc/update! conn :slack_coaching_users
                    {:active 0}
                    ["email = ? AND team_id = ?" email team-id]))))

(defn list-coaching-users [ds team-id]
  (let [team-internal-id (get-team-id ds team-id)
        query (coaching-users-query team-internal-id)
        users (jdbc/query ds query)]
    (map (partial convert-user team-id) users)))

(defn replace-base-questions!
  "Used to replace the default base questions for testing. Give it a
   datasource and a list of strings."
  [ds questions]
  (jdbc/execute! ds ["delete from base_questions"])

  (->> questions
       (map #(hash-map :question %))
       (jdbc/insert-multi! ds :base_questions)))

(defn- get-user-id [conn slack-user-id]
  (let [user-id-query (-> (h/select :id)
                          (h/from :slack_coaching_users)
                          (h/where [:= :remote_user_id slack-user-id])
                          sql/format)]
    (-> (jdbc/query conn user-id-query) first :id)))

(defn question-for-sending [ds qid {:keys [team-id] slack-user-id :id} send-fn]
  (jdbc/with-db-transaction
    [conn ds]
    (let [user-id (get-user-id conn slack-user-id)
          team-id (get-team-id ds team-id)
          base-query (h/from (h/select :id :question
                                       (sql/raw "'base' AS QTYPE"))
                             :base_questions)
          base-query (if qid (h/where base-query [:= :id qid])
                             base-query)
          custom-question-query
          (-> (h/select :id :question (sql/raw "'custom' AS QTYPE"))
              (h/from :custom_questions)
              (h/where [:and
                        [:= :team_id team-id]
                        [:= :slack_user_id user-id]
                        [:= :answered 0]]))

          query (-> {:union-all [custom-question-query base-query]}
                    (h/limit 1)
                    sql/format)
          {new-qid :id :keys [question qtype]} (first (jdbc/query conn query))
          custom-question? (= "custom" qtype)
          custom-cols [:cquestion_id :asked_cqid]
          base-cols [:question_id :asked_qid]
          [qa-col asked-col] (if custom-question? custom-cols base-cols)]
      (send-fn question)
      (jdbc/insert! conn :questions_asked
                    {:slack_user_id user-id qa-col new-qid})
      (jdbc/update! conn :slack_coaching_users
                    {asked-col new-qid :last_question_date (env/now)}
                    ["id  = ?" user-id])
      (when-not custom-question?
        (jdbc/update! conn :slack_coaching_users
                      {:asked_cqid nil} ["id  = ?" user-id])))))

(defn add-custom-question! [ds {:keys [team-id] slack-user-id :id} question]
  (jdbc/with-db-transaction
    [conn ds]
    (let [user-id (get-user-id conn slack-user-id)
          team-id (get-team-id ds team-id)]
      (jdbc/insert! conn :custom_questions {:slack_user_id user-id
                                            :team_id team-id
                                            :question question}))))

(defn- find-next-base-question [ds qid]
  (let [query (-> (h/select :*)
                  (h/from :base_questions)
                  (h/where [:> :id qid])
                  sql/format)]
    (->> query (jdbc/query ds) first :id)))

(defn next-question-for-sending! [ds qid user send-fn]
  (let [qid (if-not qid qid (find-next-base-question ds qid))]
    (question-for-sending ds qid user send-fn)))

(defn submit-answer! [ds team-id user-email qid cqid text]
  (jdbc/with-db-transaction
    [conn ds]
    (let [{:keys [id]} (get-coaching-user-raw conn team-id user-email)
          answered-col (if cqid :cquestion_id :question_id)
          which-qid (or cqid qid)]
      (jdbc/insert! conn :question_answers
                    {:slack_user_id id answered-col which-qid :answer text})
      (if cqid
        (jdbc/update! conn :custom_questions {:answered 1} ["id = ?" cqid])
        (jdbc/update! conn :slack_coaching_users {:answered_qid qid}
                      ["id = ?" id])))))

(defn list-questions-asked [ds team-id user-email]
  (let [{:keys [id]} (get-coaching-user-raw ds team-id user-email)
        query (-> (h/select :bq.question)
                  (h/from [:questions_asked :qa])
                  (h/join [:base_questions :bq] [:= :bq.id :qa.question_id])
                  (h/where [:= :slack_user_id id])
                  (h/order-by :qa.created_date)
                  sql/format)]
    (jdbc/query ds query)))

(defn list-answers [ds team-id user-email]
  (let [{:keys [id]} (get-coaching-user-raw ds team-id user-email)
        query (-> (h/select :bq.question
                            [:cq.question :cquestion]
                            [:qa.answer :qa])
                  (h/from [:question_answers :qa])
                  (h/left-join [:base_questions :bq]
                               [:= :bq.id :qa.question_id]
                               [:custom_questions :cq]
                               [:= :cq.id :qa.cquestion_id])
                  (h/where [:= :qa.slack_user_id id])
                  sql/format)]
    (->> query
         (jdbc/query ds)
         (map #(let [{:keys [question cquestion qa]} %
                     q (or question cquestion)]
                 {:question q :answer (db/extract-character-data qa)})))))

(defn reset-all-coaching-users!
  "Marks all coaching users as having last been asked a question a day ago.
   Only works against H2 because of the DATEADD function."
  [ds]
  (jdbc/with-db-transaction
    [conn ds]
    (jdbc/execute!
      conn
      [(str "update slack_coaching_users "
            "set last_question_date = DATEADD('HOUR', -16, "
            "CURRENT_TIMESTAMP())")])))

(defn list-coaching-users-across-all-teams [ds]
  (let [users
        (jdbc/query ds (->
                         (h/select :st.team_id :scu.remote_user_id
                                   :scu.asked_qid :scu.answered_qid)
                         (h/from [:slack_teams :st])
                         (h/join [:slack_coaching_users :scu]
                                 [:= :scu.team_id :st.id])
                         (h/where [:= :scu.active true])
                         sql/format))]
    (map #(let [{:keys [team_id] :as user} %]
            (convert-user team_id user)) users)))