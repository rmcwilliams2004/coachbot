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
            [clojure.java.jdbc :as jdbc]
            [clojure.set :as set]
            [honeysql.core :as sql]
            [honeysql.helpers :as h]
            [taoensso.timbre :as log]
            [coachbot.db :as db]))

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
    (-> (h/select :*)
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
          (dissoc x :created-date :updated-date :id :team-id :active)
          (assoc x :team-id team-id)
          (set/rename-keys x {:remote-user-id :id}))))

(defn get-coaching-user [ds team-id user-email]
  (convert-user team-id (get-coaching-user-raw ds team-id user-email)))

(defn add-coaching-user! [ds {:keys [email team-id] :as user}]
  (jdbc/with-db-transaction
    [conn ds]
    (let [existing-record (get-coaching-user conn team-id email)
          team-id (get-team-id ds team-id)
          new-record (as-> user x
                           (cske/transform-keys csk/->snake_case x)
                           (assoc x :team_id team-id)
                           (set/rename-keys x {:id :remote_user_id}))]
      (if existing-record
        (jdbc/update! conn :slack_coaching_users
                      {:active 1}
                      ["email = ? AND team_id = ?" email team-id])
        (jdbc/insert! conn :slack_coaching_users new-record)))))

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

(defn same-question-for-sending! [ds qid {slack-user-id :id} send-fn]
  (jdbc/with-db-transaction [conn ds]
    (let [user-id-query (-> (h/select :id)
                            (h/from :slack_coaching_users)
                            (h/where [:= :remote_user_id slack-user-id])
                            sql/format)
          [{user-id :id}] (jdbc/query conn user-id-query)
          base-query (-> (h/select :id :question)
                         (h/from :base_questions))
          query (-> (if qid (h/where base-query [:= :id qid])
                            base-query)
                    (h/order-by :id)
                    (h/limit 1)
                    sql/format)
          {new-qid :id :keys [question]} (first (jdbc/query conn query))]
      (send-fn question)
      (jdbc/insert! conn :questions_asked {:slack_user_id user-id
                                           :question_id new-qid})
      (jdbc/update! conn :slack_coaching_users
                    {:asked_qid new-qid} ["id = ?" user-id]))))

(defn- find-next-question [ds qid]
  (let [query (-> (h/select :*)
                  (h/from :base_questions)
                  (h/where [:> :id qid])
                  sql/format)]
    (->> query (jdbc/query ds) first :id)))

(defn next-question-for-sending! [ds qid user send-fn]
  (let [qid
        (if-not qid qid (find-next-question ds qid))]
    (same-question-for-sending! ds qid user send-fn)))

(defn submit-answer! [ds team-id user-email qid text]
  (jdbc/with-db-transaction [conn ds]
    (let [{:keys [id] :as user} (get-coaching-user-raw conn team-id user-email)]
      (jdbc/insert! conn :question_answers {:slack_user_id id
                                            :question_id qid
                                            :answer text})
      (jdbc/update! conn :slack_coaching_users
                    {:answered_qid qid} ["id = ?" id]))))

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
        query (-> (h/select :bq.question :qa.answer)
                  (h/from [:question_answers :qa])
                  (h/join [:base_questions :bq] [:= :bq.id :qa.question_id])
                  (h/where [:= :slack_user_id id])
                  (h/order-by :qa.created_date)
                  sql/format)]
    (->> query
         (jdbc/query ds)
         (map #(let [{:keys [question answer]} %]
                {:question question
                 :answer (db/extract-character-data answer)})))))