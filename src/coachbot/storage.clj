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
            [clj-time.core :as t]
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

(defmacro with-access-tokens [ds team-id binding & body]
  `(let [[~(first binding) ~(second binding)]
         (get-access-tokens ~ds ~team-id)]
     ~@body))

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
          (dissoc x :id :team-id)
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
                           (assoc x :created_date (env/now)) ;for unit tests
                           (set/rename-keys x {:id :remote_user_id}))]
      (if existing-record
        (let [fields {:active true}
              fields (if coaching-time
                       (assoc fields :coaching_time coaching-time)
                       fields)
              update-stmt (-> (h/update :slack_coaching_users)
                              (h/sset fields)
                              (h/where [:and [:= :email email]
                                        [:= :team_id team-id]])
                              sql/format)]
          (jdbc/execute! conn update-stmt))
        (do (jdbc/insert! conn :slack_coaching_users new-record) true)))))

(defn remove-coaching-user! [ds {:keys [email team-id]}]
  (jdbc/with-db-transaction
    [conn ds]
    (let [team-id (get-team-id ds team-id)]
      (jdbc/update! conn :slack_coaching_users
                    {:active 0}
                    ["email = ? AND team_id = ?" email team-id]))))

(defn replace-base-questions-with-groups!
  "Used to replace the default base questions for testing. Give it a
   datasource and a list of objects of the form
   {:question String, :groups [String]}. The :groups key is
   optional"
  [ds questions]
  (let [base-questions (map #(select-keys % [:question]) questions)
        all-groups (->> questions
                        (map :groups)
                        flatten
                        (into #{})
                        (remove nil?)
                        (map (partial hash-map :group_name)))]
    (jdbc/with-db-transaction
      [conn ds]
      (jdbc/execute! conn ["delete from base_questions"])
      (jdbc/insert-multi! ds :base_questions base-questions)
      (when (seq all-groups)
        (jdbc/execute! conn ["delete from question_groups"])
        (jdbc/insert-multi! ds :question_groups all-groups)
        (let [question-groups (jdbc/query conn (-> (h/select :*)
                                                   (h/from :question_groups)
                                                   sql/format))
              question-groups (apply merge
                                     (map #(let [{:keys [id group_name]} %]
                                             {group_name id}) question-groups))
              question-ids (jdbc/query conn (-> (h/select :*)
                                                (h/from :base_questions)
                                                sql/format))
              question-ids (apply merge (map #(let [{:keys [id question]} %]
                                                {question id}) question-ids))]
          (doseq [{:keys [question groups]} questions
                  group groups]
            (when (seq groups)
              (let [qid (question-ids question)
                    group-id (question-groups group)]
                (jdbc/insert! conn :bq_question_groups
                              [:question_id :question_group_id]
                              [qid group-id])))))))))

(defn replace-base-questions!
  "Used to replace the default base questions for testing. Give it a
   datasource and a list of strings."
  [ds questions]
  (replace-base-questions-with-groups!
    ds (map #(hash-map :question %) questions)))

(defn- get-user-id [conn slack-user-id]
  (let [user-id-query (-> (h/select :id)
                          (h/from :slack_coaching_users)
                          (h/where [:= :remote_user_id slack-user-id])
                          sql/format)]
    (-> (jdbc/query conn user-id-query) first :id)))

(defn- by-group-name [group] [:= :qg.group_name group])

(defn- select-from-groups [select-stmt ds slack-user-id & [group]]
  (let [limit-by-user [:= :scu.remote_user_id slack-user-id]]
    (-> select-stmt
        (h/from [:scu_question_groups :scuqg])
        (h/join [:slack_coaching_users :scu]
                [:= :scu.id :scuqg.scu_id]
                [:question_groups :qg]
                [:= :qg.id :scuqg.question_group_id])
        (h/where
          (if group
            [:and limit-by-user (by-group-name group)]
            limit-by-user)))))

(defn list-groups-for-user [ds slack-user-id]
  (jdbc/query ds (-> (h/select :qg.id :qg.group_name)
                     (select-from-groups ds slack-user-id)
                     sql/format)))

(defn- base-question-query [operand qid groups]
  (let [qid-clause (when qid [operand :bq.id qid])
        group-clause (when (seq groups) [:in :bqg.question_group_id groups])]
    (-> (h/select :bq.id :bq.question (sql/raw "'base' AS QTYPE"))
        (h/from [:base_questions :bq])
        (h/left-join [:bq_question_groups :bqg]
                     [:= :bqg.question_id :bq.id])
        (h/where (if qid-clause
                   (if group-clause [:and qid-clause group-clause] qid-clause)
                   (when group-clause group-clause))))))

(defn- custom-question-query [user-id]
  (-> (h/select :id :question (sql/raw "'custom' AS QTYPE"))
      (h/from :custom_questions)
      (h/where [:and
                [:= :slack_user_id user-id]
                [:= :answered 0]])))

(defn question-for-sending [ds qid {slack-user-id :id} send-fn]
  (jdbc/with-db-transaction
    [conn ds]
    (let [user-id (get-user-id conn slack-user-id)
          groups (list-groups-for-user conn slack-user-id)
          query (-> {:union-all
                     [(custom-question-query user-id)
                      (base-question-query := qid (map :id groups))]}
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

(defn add-custom-question! [ds {slack-user-id :id} question]
  (jdbc/with-db-transaction
    [conn ds]
    (let [user-id (get-user-id conn slack-user-id)]
      (jdbc/insert! conn :custom_questions {:slack_user_id user-id
                                            :question question}))))

(defn- find-next-base-question [ds qid group-ids]
  (->> (base-question-query :> qid group-ids)
       sql/format
       (jdbc/query ds)
       first
       :id))

(defn next-question-for-sending! [ds qid {slack-user-id :id :as user} send-fn]
  (let [user-groups (map :id (list-groups-for-user ds slack-user-id))
        qid (if-not qid qid (find-next-base-question ds qid user-groups))]
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
  "Marks all coaching users as having last been asked a question 16 hours ago."
  [ds]
  (jdbc/with-db-transaction
    [conn ds]
    (jdbc/execute!
      conn
      ["update slack_coaching_users set last_question_date = ?"
       (t/minus (env/now) (t/hours 16))])))

(defn list-coaching-users-across-all-teams [ds]
  (let [users
        (jdbc/query ds (->
                         (h/select :st.team_id :scu.remote_user_id
                                   :scu.asked_qid :scu.answered_qid
                                   :scu.last_question_date :scu.created_date
                                   :scu.coaching_time :scu.timezone)
                         (h/from [:slack_teams :st])
                         (h/join [:slack_coaching_users :scu]
                                 [:= :scu.team_id :st.id])
                         (h/where [:= :scu.active true])
                         sql/format))]
    (map #(let [{:keys [team_id] :as user} %]
            (convert-user team_id user)) users)))

(defn- from-question-groups []
  (-> (h/select :qg.id :qg.group_name)
      (h/from [:question_groups :qg])
      (h/order-by :qg.group_name)))

(defn list-question-groups [ds]
  (let [groups (jdbc/query ds (sql/format (from-question-groups)))]
    (map :group_name groups)))

(defn- from-question-groups-by-name [group]
  (h/where (from-question-groups) (by-group-name group)))

(defn is-in-question-group? [ds slack-user-id group]
  (as-> (h/select :scuqg.scu_id) x
        (select-from-groups x ds slack-user-id group)
        (sql/format x)
        (jdbc/query ds x)
        (first x)))

(defmacro with-question-group-context [conn slack-user-id group bindings & body]
  `(let [{~(first bindings) :id} (as-> (from-question-groups-by-name ~group) x#
                                       (sql/format x#)
                                       (jdbc/query ~conn x#)
                                       (first x#))
         ~(second bindings) (get-user-id ~conn ~slack-user-id)]
     (when ~(first bindings)
       (do ~@body))))

(defn add-to-question-group! [ds slack-user-id group]
  (jdbc/with-db-transaction
    [conn ds]
    (with-question-group-context conn slack-user-id group [group-id user-id]
      (jdbc/execute! conn (-> (h/insert-into :scu_question_groups)
                              (h/values [{:scu_id user-id
                                          :question_group_id group-id}])
                              sql/format)))))

(defn remove-from-question-group! [ds slack-user-id group]
  (jdbc/with-db-transaction
    [conn ds]
    (with-question-group-context conn slack-user-id group [group-id user-id]
      (jdbc/execute! conn (-> (h/delete-from :scu_question_groups)
                              (h/where [:and
                                        [:= :scu_id user-id]
                                        [:= :question_group_id group-id]])
                              sql/format)))))