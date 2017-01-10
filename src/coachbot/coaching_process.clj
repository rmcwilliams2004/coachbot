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

(ns coachbot.coaching-process
  (:require [clj-cron-parse.core :as cp]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
            [clojurewerkz.quartzite.jobs :as qj]
            [clojurewerkz.quartzite.schedule.cron :as qc]
            [clojurewerkz.quartzite.scheduler :as qs]
            [clojurewerkz.quartzite.triggers :as qt]
            [coachbot.db :as db]
            [coachbot.env :as env]
            [coachbot.messages :as messages]
            [coachbot.slack :as slack]
            [coachbot.storage :as storage]
            [taoensso.timbre :as log]))

(defn stop-coaching! [team-id channel user-id _]
  (let [ds (db/datasource)]
    (storage/with-access-tokens ds team-id [access-token bot-access-token]
      (storage/remove-coaching-user!
        ds (slack/get-user-info access-token user-id))
      (slack/send-message! bot-access-token channel
                           messages/coaching-goodbye))))

(defn register-custom-question! [team-id user-id question]
  (let [ds (db/datasource)]
    (storage/with-access-tokens ds team-id [access-token _]
      (storage/add-custom-question!
        ds (slack/get-user-info access-token user-id) question))))

(defmacro with-sending-constructs
  [slack-user-id slack-team-id channel bindings & body]
  `(let [~(first bindings) (db/datasource)]
     (storage/with-access-tokens ~(first bindings) ~slack-team-id
       [~(nth bindings 2) bot-access-token#]
       (let [~(second bindings)
             (partial slack/send-message! bot-access-token#
                      (or ~channel ~slack-user-id))]
         ~@body))))

(defn send-new-question!
  "Sends a new question to a specific individual."
  [{:keys [id asked-qid team-id] :as user} & [channel]]

  (with-sending-constructs id team-id channel [ds send-fn _]
    (let [{:keys [cquestion_id slack_user_id answered]}
          (storage/get-last-question-asked ds user)]
      (jdbc/with-db-transaction [conn ds]
        (when (and cquestion_id (not answered))
          (storage/mark-custom-question! conn slack_user_id cquestion_id
                                         :skipped))
        (storage/next-question-for-sending! conn asked-qid user send-fn)))))

(defn- send-next-or-resend-prev-question!
  ([user] (send-next-or-resend-prev-question! user nil))
  ([{:keys [id asked-qid answered-qid
            team-id]
     :as user} channel]
   (with-sending-constructs id team-id channel [ds send-fn _]
     (if (= asked-qid answered-qid)
       (storage/next-question-for-sending! ds asked-qid user send-fn)
       (storage/question-for-sending ds asked-qid user send-fn)))))

(defn send-question-if-conditions-are-right!
  "Sends a question to a specific individual only if the conditions are
   right. Checks if the previous question was asked before deciding to re-send
   that one or send a new one. Also checks if the current time is on or after
   the time that the user requested a question to be asked, in their requested
   timezone, of either the last time they were asked a question or the day
   their user record was created relative to the beginning of the day."
  [{:keys [id last-question-date created-date coaching-time timezone]
    :as user}
   & [channel]]
  (let [start-time (or last-question-date
                       (t/with-time-at-start-of-day created-date))
        next-date (cp/next-date start-time coaching-time timezone)
        now (env/now)
        should-send-question? (or (t/equal? now next-date)
                                  (t/after? now next-date))
        formatter (tf/formatters :date-time)]
    (log/debugf
      "User %s: ct='%s', tz='%s', st='%s', nd='%s', now='%s', send? %s"
      id coaching-time timezone start-time
      (tf/unparse formatter next-date)
      (tf/unparse formatter now) should-send-question?)

    (when should-send-question?
      (send-next-or-resend-prev-question! user channel)

      ; don't overrun the slack servers
      (Thread/sleep 500))))

(defn start-coaching!
  [team-id user-id & [coaching-time]]
  (let [ds (db/datasource)]
    (storage/with-access-tokens ds team-id [access-token _]
      (storage/add-coaching-user!
        ds (if coaching-time
             (assoc
               (slack/get-user-info access-token user-id)
               :coaching-time coaching-time)
             (slack/get-user-info access-token user-id))))))

(defn submit-text! [team-id user-email text]
  ;; If there is an outstanding for the user, submit that
  ;; Otherwise store it someplace for a live person to review
  (let [ds (db/datasource)
        {:keys [id asked-qid asked-cqid answered-qid]}
        (storage/get-coaching-user ds team-id user-email)]
    (storage/with-access-tokens ds team-id [_ bot-access-token]
      (if (or asked-cqid
              (and asked-qid (not= asked-qid answered-qid)))
        (do
          (storage/submit-answer!
            ds team-id user-email asked-qid asked-cqid text)
          (slack/send-message! bot-access-token id messages/thanks-for-answer))
        (slack/send-message! bot-access-token id messages/unknown-command)))))

(defn ensure-user! [ds access-token team-id user-id]
  (let [{:keys [email] :as user} (slack/get-user-info access-token user-id)
        get-coaching-user #(storage/get-coaching-user ds team-id email)]
    (if-let [result (get-coaching-user)]
      result
      (do
        (storage/add-coaching-user! ds user)
        (storage/remove-coaching-user! ds user)
        (get-coaching-user)))))

(defn next-question!
  ([team_id channel user-id _] (next-question! team_id channel user-id))
  ([team_id channel user-id]
   (let [ds (db/datasource)]
     (storage/with-access-tokens ds team_id [access-token _]
       (send-new-question!
         (ensure-user! ds access-token team_id user-id) channel)))))

(defn- question-group-display [ds user-id]
  (let [user-groups (storage/list-groups-for-user ds user-id)]
    (str "You are in: "
         (if-not (seq user-groups)
           "no groups. You get all the questions!"
           (str/join ", " (map :group_name user-groups))))))

(defn show-question-groups [team-id channel user-id _]
  (with-sending-constructs user-id team-id channel [ds send-fn _]
    (let [groups (storage/list-question-groups ds)]
      (send-fn (str
                 "The following groups are available:\n\n"
                 (str/join "\n" groups) "\n\n"
                 (question-group-display ds user-id))))))

(defn add-to-question-group! [team-id channel user-id [group]]
  (with-sending-constructs user-id team-id channel [ds send-fn access-token]
    (ensure-user! ds access-token team-id user-id)
    (send-fn
      (if (storage/is-in-question-group? ds user-id group)
        (str "Congrats. You're already a member of " group)
        (if (seq (storage/add-to-question-group! ds user-id group))
          (str "I'll send you questions from " group "\n\n"
               (question-group-display ds user-id))
          (str group " does not exist."))))))

(defn remove-from-question-group! [team-id channel user-id [group]]
  (with-sending-constructs user-id team-id channel [ds send-fn _]
    (send-fn
      (if (storage/is-in-question-group? ds user-id group)
        (if (seq (storage/remove-from-question-group! ds user-id group))
          (str "Ok. I'll stop sending you questions from " group "\n\n"
               (question-group-display ds user-id))
          (do
            (log/errorf "Failed to remove %s from %s" user-id group)
            (str "Sorry, but we had a problem removing you. We'll look "
                 "into it.")))
        (str "No worries; you're not in " group)))))

(defn send-next-question-to-everyone-everywhere! []
  (let [ds (db/datasource)
        users (storage/list-coaching-users-across-all-teams ds)]
    (log/debugf "Users to send to: %d" (count users))
    (doall (map send-question-if-conditions-are-right! users))))

(qj/defjob DailyCoachingJob [ctx]
  (try
    (send-next-question-to-everyone-everywhere!)
    (catch Throwable t
      (log/errorf t "Unable to send next question to everyone everywhere"))))

(defn schedule-individual-coaching! [scheduler]
  (let [job (qj/build
              (qj/of-type DailyCoachingJob)
              (qj/with-identity (qj/key "jobs.coaching.individual")))
        trigger (qt/build
                  (qt/with-identity (qt/key "triggers.every-minute"))
                  (qt/start-now)
                  (qt/with-schedule
                    (qc/schedule (qc/cron-schedule "0 * * ? * *"))))]
    (qs/schedule scheduler job trigger)))