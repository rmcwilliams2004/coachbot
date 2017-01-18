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
            [clojure.string :as str]
            [coachbot.db :as db]
            [coachbot.env :as env]
            [coachbot.hsql-utils :as hu]
            [honeysql.core :as sql]
            [honeysql.helpers :as h]
            [slingshot.slingshot :as ss]
            [taoensso.timbre :as log]))

(defn get-access-tokens [ds slack-team-id]
  (let [[{:keys [access_token bot_access_token]}]
        (-> (h/select [:access_token "access_token"]
                      [:bot_access_token "bot_access_token"])
            (h/from :slack_teams)
            (h/where [:= :team_id slack-team-id])
            (hu/query ds))]
    [access_token bot_access_token]))

(defmacro with-access-tokens [ds team-id bindings & body]
  `(let [[~(first bindings) ~(second bindings)]
         (get-access-tokens ~ds ~team-id)]
     ~@body))

(defn get-bot-user-id [ds slack-team-id]
  (let [[{:keys [bot_user_id]}] (-> (h/select [:bot_user_id "bot_user_id"])
                                    (h/from :slack_teams)
                                    (h/where [:= :team_id slack-team-id])
                                    (hu/query ds))]
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

(defn get-team-id [ds slack-team-id]
  (-> (h/select :id)
      (h/from :slack_teams)
      (h/where [:= :team_id slack-team-id])
      (hu/query ds)
      first
      :id))

(defn- get-coaching-user-raw [ds slack-team-id user-email]
  (let [team-internal-id (get-team-id ds slack-team-id)
        where-clause [:and
                      [:= :team_id team-internal-id]
                      [:= :email user-email]]]
    (-> (h/select :*)
        (h/from :slack_coaching_users)
        (h/where where-clause)
        (hu/query ds)
        first)))

(defn- convert-user [team-id user]
  (when user
    (as-> user x
          (cske/transform-keys csk/->kebab-case x)
          (dissoc x :id :team-id)
          (assoc x :team-id team-id)
          (set/rename-keys x {:remote-user-id :id}))))

(defn get-coaching-user [ds slack-team-id user-email]
  (convert-user slack-team-id
                (get-coaching-user-raw ds slack-team-id user-email)))

(defn add-coaching-user! [ds {:keys [email team-id coaching-time] :as user}]
  (jdbc/with-db-transaction
    [conn ds]
    (let [existing-record (get-coaching-user conn team-id email)
          db-team-id (get-team-id ds team-id)
          new-record (as-> user x
                           (cske/transform-keys csk/->snake_case x)
                           (assoc x :team_id db-team-id)
                           (assoc x :created_date (env/now)) ;for unit tests
                           (set/rename-keys x {:id :remote_user_id})
                           (dissoc x :bot? :deleted?))]
      (when-not db-team-id (ss/throw+ {:type ::invalid-team :team-id team-id}))

      (if existing-record
        (let [fields {:active true}
              fields (if (contains? user :coaching-time)
                       (assoc fields :coaching_time coaching-time)
                       fields)
              update-stmt (-> (h/update :slack_coaching_users)
                              (h/sset fields)
                              (h/where [:and [:= :email email]
                                        [:= :team_id db-team-id]]))]
          (hu/execute-safely! update-stmt conn))
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
        (let [question-groups (-> (h/select :*)
                                  (h/from :question_groups)
                                  (hu/query conn))
              question-groups (apply merge
                                     (map #(let [{:keys [id group_name]} %]
                                             {group_name id}) question-groups))
              question-ids (-> (h/select :*)
                               (h/from :base_questions)
                               (hu/query conn))
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

(defn- where-remote-user-id [stmt slack-user-id]
  (h/where stmt [:= :scu.remote_user_id slack-user-id]))

(defn- get-user-id [conn slack-user-id]
  (-> (h/select :scu.id)
      (h/from [:slack_coaching_users :scu])
      (where-remote-user-id slack-user-id)
      (hu/query conn)
      first
      :id))

(defn- by-group-name [group] [:= :qg.group_name group])

(defn- select-from-groups [select-stmt slack-user-id & [group]]
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
  (-> (h/select :qg.id :qg.group_name)
      (select-from-groups slack-user-id)
      (h/order-by :qg.group_name)
      (hu/query ds)))

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
                [:= :answered 0]
                [:= :skipped 0]])))

(defn get-last-question-asked [conn {remote-user-id :id}]
  (-> (h/select :qasked.slack_user_id :qasked.question_id :qasked.cquestion_id
                :cq.answered)
      (h/from [:slack_coaching_users :scu])
      (where-remote-user-id remote-user-id)
      (h/join [:questions_asked :qasked]
              [:= :qasked.slack_user_id :scu.id])
      (h/left-join [:custom_questions :cq]
                   [:= :cq.id :qasked.cquestion_id])
      (h/order-by [:qasked.id :desc])
      (h/limit 1)
      (hu/query conn)
      first))

(defn mark-custom-question! [conn slack-user-id question-id field]
  (jdbc/update! conn :custom_questions {field 1}
                ["id = ? AND slack_user_id = ?" question-id slack-user-id]))

(defn- from-question-groups []
  (-> (h/select :qg.id :qg.group_name)
      (h/from [:question_groups :qg])
      (h/order-by :qg.group_name)))

(defn get-groups-for-qid [ds new-qid]
  (-> (from-question-groups)
      (h/join [:bq_question_groups :bqg]
              [:= :bqg.question_group_id :qg.id])
      (h/where [:= :bqg.question_id new-qid])
      (hu/query ds :group_name)))

(defn- add-question-metadata
  ([items question]
   (format "[%s] %s"
           (->> items
                (map #(let [{:keys [emphasize? msg]} %]
                        (if emphasize? (format "_%s_" msg) msg)))
                (str/join ", ")) question))
  ([ds slack-user-id new-qid question custom-question?]
   (if custom-question?
     (add-question-metadata [{:emphasize? true
                              :msg "Custom Question"}] question)
     (let [groups (get-groups-for-qid ds new-qid)
           groups-for-user (->> slack-user-id
                                (list-groups-for-user ds)
                                (map :group_name)
                                (into #{}))]
       (if (seq groups)
         (add-question-metadata (map #(hash-map :msg %
                                                :emphasize? (groups-for-user %))
                                     (get-groups-for-qid ds new-qid))
                                question)
         question)))))

(defn question-for-sending! [ds qid {remote-user-id :id}]
  (jdbc/with-db-transaction [conn ds]
    (let [user-id (get-user-id conn remote-user-id)
          groups (list-groups-for-user conn remote-user-id)

          [{new-qid :id :keys [question qtype]}]
          (-> {:union-all
               [(custom-question-query user-id)
                (base-question-query := qid (map :id groups))]}
              (h/limit 1)
              (hu/query conn))

          custom-question? (= "custom" qtype)
          custom-cols [:cquestion_id :asked_cqid]
          base-cols [:question_id :asked_qid]
          [qa-col asked-col] (if custom-question? custom-cols base-cols)
          question (add-question-metadata ds remote-user-id new-qid question
                                          custom-question?)]
      (jdbc/insert! conn :questions_asked
                    {:slack_user_id user-id qa-col new-qid})
      (jdbc/update! conn :slack_coaching_users
                    {asked-col new-qid :last_question_date (env/now)}
                    ["id  = ?" user-id])
      (when-not custom-question?
        (jdbc/update! conn :slack_coaching_users {:asked_cqid nil}
                      ["id  = ?" user-id]))
      question)))

(defn add-custom-question! [ds {remote-user-id :id} question]
  (jdbc/with-db-transaction [conn ds]
    (let [user-id (get-user-id conn remote-user-id)]
      (jdbc/insert! conn :custom_questions {:slack_user_id user-id
                                            :question question}))))

(defn- find-next-base-question [ds qid group-ids]
  (-> (base-question-query :> qid group-ids)
      (hu/query ds)
      first
      :id))

(defn next-question-for-sending! [ds qid {remote-user-id :id :as user}]
  (let [user-groups (map :id (list-groups-for-user ds remote-user-id))
        qid (if-not qid qid (find-next-base-question ds qid user-groups))]
    (question-for-sending! ds qid user)))

(defn submit-answer! [ds slack-team-id user-email qid cqid text]
  (jdbc/with-db-transaction [conn ds]
    (let [{:keys [id]} (get-coaching-user-raw conn slack-team-id user-email)
          answered-col (if cqid :cquestion_id :question_id)
          which-qid (or cqid qid)]
      (jdbc/insert! conn :question_answers
                    {:slack_user_id id answered-col which-qid :answer text})
      (if cqid
        (mark-custom-question! conn id cqid :answered)
        (jdbc/update! conn :slack_coaching_users {:answered_qid qid}
                      ["id = ?" id])))))

(defn list-questions-asked [ds slack-team-id user-email]
  (let [{:keys [id]} (get-coaching-user-raw ds slack-team-id user-email)]
    (-> (h/select :bq.question)
        (h/from [:questions_asked :qa])
        (h/join [:base_questions :bq] [:= :bq.id :qa.question_id])
        (h/where [:= :slack_user_id id])
        (h/order-by :qa.created_date)
        (hu/query ds))))

(defn list-answers [ds slack-team-id user-email]
  (let [{:keys [id]} (get-coaching-user-raw ds slack-team-id user-email)
        answers (-> (h/select :bq.question
                              [:cq.question :cquestion]
                              [:qa.answer :qa])
                    (h/from [:question_answers :qa])
                    (h/left-join [:base_questions :bq]
                                 [:= :bq.id :qa.question_id]
                                 [:custom_questions :cq]
                                 [:= :cq.id :qa.cquestion_id])
                    (h/where [:= :qa.slack_user_id id])
                    (hu/query ds))]
    (map #(let [{:keys [question cquestion qa]} %
                q (or question cquestion)]
            {:question q :answer (db/extract-character-data qa)}) answers)))

(defn reset-all-coaching-users!
  "Marks all coaching users as having last been asked a question 16 hours ago."
  [ds]
  (jdbc/with-db-transaction [conn ds]
    (jdbc/execute! conn
      ["update slack_coaching_users set last_question_date = ?"
       (t/minus (env/now) (t/hours 16))])))

(defn list-coaching-users-across-all-teams [ds]
  (let [users
        (-> (h/select :st.team_id :scu.remote_user_id
                      :scu.asked_qid :scu.answered_qid
                      :scu.last_question_date :scu.created_date
                      :scu.coaching_time :scu.timezone)
            (h/from [:slack_teams :st])
            (h/join [:slack_coaching_users :scu]
                    [:= :scu.team_id :st.id])
            (h/where [:= :scu.active true])
            (hu/query ds))]
    (map #(let [{:keys [team_id] :as user} %]
            (convert-user team_id user)) users)))

(defn list-question-groups [ds]
  (let [groups (hu/query (from-question-groups) ds)]
    (map :group_name groups)))

(defn- from-question-groups-by-name [group]
  (h/where (from-question-groups) (by-group-name group)))

(defn is-in-question-group? [ds remote-user-id group]
  (-> (h/select :scuqg.scu_id)
      (select-from-groups remote-user-id group)
      (hu/query ds)
      first))

(defmacro with-question-group-context
  [conn remote-user-id group bindings & body]
  `(let [{~(first bindings) :id} (-> (from-question-groups-by-name ~group)
                                     (hu/query ~conn)
                                     first)
         ~(second bindings) (get-user-id ~conn ~remote-user-id)]
     (when ~(first bindings)
       (do ~@body))))

(defn add-to-question-group! [ds remote-user-id group]
  (jdbc/with-db-transaction [conn ds]
    (with-question-group-context conn remote-user-id group [group-id user-id]
      (-> (h/insert-into :scu_question_groups)
          (h/values [{:scu_id user-id
                      :question_group_id group-id}])
          (hu/execute-safely! conn)))))

(defn remove-from-question-group! [ds remote-user-id group]
  (jdbc/with-db-transaction [conn ds]
    (with-question-group-context conn remote-user-id group [group-id user-id]
      (-> (h/delete-from :scu_question_groups)
          (h/where [:and
                    [:= :scu_id user-id]
                    [:= :question_group_id group-id]])
          (hu/execute-safely! conn)))))

(def ^:private slack-coaching-channels [:slack_coaching_channels :scc])

(defn- where-is-channel [hq team-id channel]
  (h/where hq [:and [:= :scc.team_id team-id]
               [:= :scc.channel_id channel]]))

(defn- load-channel [conn slack-team-id channel field]
  (-> (h/select field)
      (h/from slack-coaching-channels)
      (where-is-channel slack-team-id channel)
      (hu/query conn)
      first))

(defn- set-channel-active! [conn slack-team-id channel active?]
  (-> (h/update slack-coaching-channels)
      (h/sset {:active active?})
      (where-is-channel slack-team-id channel)
      (hu/execute-safely! conn)))

(defn add-coaching-channel! [ds slack-team-id channel]
  (jdbc/with-db-transaction [conn ds]
    (let [team-id (get-team-id ds slack-team-id)
          new-record {:team_id team-id :channel_id channel}
          existing-record (load-channel conn team-id channel :scc.channel_id)]
      (if existing-record
        (set-channel-active! conn team-id channel true)
        (do (jdbc/insert! conn :slack_coaching_channels new-record) true)))))

(defn stop-coaching-channel! [ds slack-team-id channel]
  (jdbc/with-db-transaction [conn ds]
    (let [team-id (get-team-id ds slack-team-id)
          existing-record (load-channel conn team-id channel :scc.id)]
      (if existing-record
        (set-channel-active! conn team-id channel false)
        (ss/throw+ {:type :channel-not-found
                    :team team-id
                    :channel channel})))))

(defn- from-coaching-channels [conn slack-team-id field & where-clauses]
  (let [select-field (keyword (str "scc." (name field)))]
    (-> (h/select select-field)
        (h/from slack-coaching-channels)
        (h/join [:slack_teams :st]
                [:= :st.id :scc.team_id])
        (h/where (into [:and
                        [:= :st.team_id slack-team-id]
                        [:= :scc.active true]] where-clauses))
        (hu/query conn field))))

(defn list-coaching-channels [conn slack-team-id]
  (from-coaching-channels conn slack-team-id :channel_id))

(defn add-channel-question! [ds slack-team-id channel question]
  (jdbc/with-db-transaction [conn ds]
    (let [team-id (get-team-id conn slack-team-id)
          cid (:id (load-channel conn team-id channel :id))]
      (-> (h/insert-into :channel_questions)
          (h/values [{:question question}])
          (hu/execute-safely! conn))

      (-> (h/insert-into :channel_questions_asked)
          (h/values [{:channel_id cid
                      :question_id (db/fetch-last-insert-id conn)
                      :expiration_timestamp (env/now)}])
          (hu/execute-safely! conn))
      (db/fetch-last-insert-id conn))))

(defn log-user-activity! [ds {:keys [team_id slack_user_id] :as activity}]
  (jdbc/with-db-transaction [conn ds]
    (let [record (-> activity
                     (update :mtype name)
                     (update :team_id (partial get-team-id conn))
                     (update :slack_user_id (partial get-user-id conn))
                     (update :raw_msg pr-str)
                     (update :processed_msg pr-str))
          {db_team_id :team_id
           db_user_id :slack_user_id} record]
      (if (and team_id slack_user_id)
        (jdbc/insert! conn :user_activity record)
        (log/errorf "Invalid team/user: t=%s/u=%s yielded %s/%s"
                    team_id slack_user_id db_team_id db_user_id)))))

(defn- where-answer-is [q question-id user-id]
  (h/where q [:and [:= :qa_id question-id]
              [:= :scu_id user-id]]))

(defn get-channel-question-response [conn slack-team-id question-id email]
  (let [{user-id :id}
        (get-coaching-user-raw conn slack-team-id email)]
    (-> (h/select :id :answer)
        (h/from :channel_question_answers)
        (where-answer-is question-id user-id)
        (hu/query conn)
        first)))

(defn get-channel-question-text [conn question-id]
  (-> (h/select :cq.question)
      (h/from [:channel_questions_asked :cqa])
      (h/join [:channel_questions :cq]
              [:= :cq.id :cqa.question_id])
      (h/where [:= :cqa.id question-id])
      (hu/query conn :question)
      first))

(defn store-channel-question-response! [ds slack-team-id email
                                        question-id answer]
  (jdbc/with-db-transaction [conn ds]
    (let [{user-id :id}
          (get-coaching-user-raw conn slack-team-id email)

          existing-answer
          (get-channel-question-response conn slack-team-id question-id email)

          [hq result]
          (if existing-answer
            [(-> (h/update :channel_question_answers)
                 (h/sset {:answer answer})
                 (where-answer-is question-id user-id)) :updated]
            [(h/values (h/insert-into :channel_question_answers)
                       [{:qa_id question-id
                         :scu_id user-id
                         :answer answer}]) :added])]
      (hu/execute-safely! hq conn)
      result)))