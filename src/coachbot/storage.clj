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
            [honeysql.core :as sql]
            [honeysql.helpers :as h]
            [taoensso.timbre :as log]
            [clojure.set :as set]
            [clojure.pprint :as pprint]))

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

(defn add-coaching-user! [ds {:keys [team-id] :as user}]
  (jdbc/with-db-transaction
    [conn ds]
    (let [team-id (get-team-id ds team-id)

          new-record (as-> user x
                           (cske/transform-keys csk/->snake_case x)
                           (assoc x :team_id team-id)
                           (set/rename-keys x {:id :remote_user_id}))]
      (jdbc/insert! conn :slack_coaching_users new-record))))

(defn remove-coaching-user! [ds {:keys [email team-id] :as user}]
  (jdbc/with-db-transaction
    [conn ds]
    (let [team-id (get-team-id ds team-id)]
      (jdbc/delete! conn :slack_coaching_users
                    ["email = ? AND team_id = ?" email team-id]))))

(defn- convert-user [team-id user]
  (as-> user x
        (cske/transform-keys csk/->kebab-case x)
        (dissoc x :created-date :updated-date :id :team-id)
        (assoc x :team-id team-id)
        (set/rename-keys x {:remote-user-id :id})))

(defn get-coaching-user [ds team-id user-id]
  (let [team-internal-id (get-team-id ds team-id)
        query (-> (h/select :*)
                  (h/from :slack_coaching_users)
                  (h/where [:and
                            [:= :team_id team-internal-id]
                            [:= :remote_user_id user-id]])
                  sql/format)
        [user] (jdbc/query ds query)]
    (convert-user team-id user)))

(defn list-coaching-users [ds team-id]
  (let [team-internal-id (get-team-id ds team-id)
        query (-> (h/select :*)
                  (h/from :slack_coaching_users)
                  (h/where [:and
                            [:= :team_id team-internal-id]])
                  sql/format)
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