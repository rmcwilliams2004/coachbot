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

(ns coachbot.events
  (:require [clojure.pprint :as pprint]
            [clojure.string :as str]
            [coachbot.coaching-process :as coaching]
            [coachbot.command-parser :as parser]
            [coachbot.db :as db]
            [coachbot.env :as env]
            [coachbot.messages :as messages]
            [coachbot.slack :as slack]
            [coachbot.storage :as storage]
            [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [schema.core :as s]
            [slingshot.slingshot :as ss]
            [taoensso.timbre :as log]
            [cheshire.core :as json])
  (:import (java.util.concurrent LinkedBlockingQueue Executors)))

(s/defschema EventMessage
  {s/Any s/Any})

(def ^:private events (atom {}))

(def ^:private event-aliases (atom {}))

(defn defevent [{:keys [command help aliases config-options]} ef]
  (swap! events assoc command
         {:help help :config-options config-options :ef ef})

  (let [aliases (or aliases [])]
    (doseq [alias (conj aliases command)]
      (swap! event-aliases assoc alias command))))

(defn- auth-success [& {:keys [access-token bot-access-token] :as auth-data}]
  (storage/store-slack-auth! (db/datasource) auth-data)
  (let [members (slack/list-members access-token)]
    (doseq [{:keys [id name first-name]} members]
      ; don't overrun the slack servers
      (Thread/sleep 500)

      (slack/send-message!
        bot-access-token id
        (format (str "Hello, %s. I'm a coaching robot. To get started, you "
                     "can say 'start coaching', otherwise say 'help' to see "
                     "what commands I respond to.")
                (or first-name name))))))

(defn- handle-unknown-failure [t event]
  (log/errorf t "Unable to handle event: %s" event)
  "Unknown failure")

(defn- handle-parse-failure [text result]
  (log/warnf "Unable to parse command: %s" text)
  (log/debugf "Parse Result: %s" result))

(defn- hello-world [team-id channel user-id _]
  (storage/with-access-tokens (db/datasource) team-id
    [access-token bot-access-token]
    (let [{:keys [first-name name]} (slack/get-user-info access-token user-id)]
      (slack/send-message! bot-access-token channel
                           (str "Hello, " (or first-name name))))))

(defn- help-for-event [event]
  (let [[command {:keys [help config-options]}] event
        config-options (or config-options {"" help})]
    (map #(let [[option help] %]
            (format " • %s%s -- %s" command option help)) config-options)))

(defn- help [team-id channel _ _]
  (storage/with-access-tokens (db/datasource) team-id [_ bot-access-token]
    (slack/send-message!
      bot-access-token channel
      (str "Here are the commands I respond to:\n"
           (str/join "\n" (flatten (map help-for-event @events)))))))

(def ^:private start-time-ptn #"(?i)(\d|1[0-2])( )?(a\.?m\.?|p\.?m\.?)")

(defn translate-start-time [start-time]
  (if start-time
    (let [start-time (str/replace start-time "." "")
          [_ hour _ time-of-day] (re-find start-time-ptn start-time)
          time-of-day (.toLowerCase time-of-day)
          hour (Integer/parseInt hour)
          hour (if (and (not= 12 hour) (= "pm" time-of-day))
                 (+ 12 hour)
                 (if (and (= "am" time-of-day) (= 12 hour)) 0 hour))]
      (format "0 0 %d * * *" hour))
    "0 0 10 * * *"))

(defn- start-coaching! [team-id channel user-id [start-time]]
  (log/debugf "start-coaching! %s %s %s %s" team-id channel user-id start-time)
  (storage/with-access-tokens (db/datasource) team-id [_ bot-access-token]
    (coaching/start-coaching! team-id user-id (translate-start-time start-time))
    (slack/send-message! bot-access-token channel
                         (format messages/coaching-hello
                                 (or start-time "10am")))))

(def hi-cmd "hi")
(def help-cmd "help")
(def start-coaching-cmd "start coaching")
(def stop-coaching-cmd "stop coaching")
(def next-question-cmd "next question")
(def another-question-cmd "another question")
(def show-question-groups-cmd "show question groups")
(def add-to-group-cmd "add to question group")
(def remove-from-group-cmd "remove from question group")

(defevent {:command hi-cmd
           :help "checks if I'm listening"} hello-world)

(defevent {:command help-cmd
           :help "display this help message"} help)

(defevent {:command start-coaching-cmd
           :config-options
           {""
            (str "send daily motivational questions at 10am every day in your "
                 "timezone")

            " at {hour}{am|pm}"
            (str "send daily motivational questions at a specific time "
                 "(e.g. 'start coaching at 9am')")}} start-coaching!)

(defevent {:command stop-coaching-cmd
           :help "stop sending questions"} coaching/stop-coaching!)

(defevent {:command next-question-cmd
           :help "ask a new question"
           :aliases [another-question-cmd]} coaching/next-question!)

(defevent {:command show-question-groups-cmd
           :help "get a list of the question groups available"}
          coaching/show-question-groups)

(defevent {:command add-to-group-cmd
           :config-options
           {" {group name}"
            (str "send questions from the given question group instead of "
                 "the default "
                 "(e.g. 'add to question group Time Management')")}}
          coaching/add-to-question-group!)

(defevent {:command remove-from-group-cmd
           :config-options
           {" {group name}"
            (str "stop sending questions from the given question group "
                 "(e.g. 'remove from question group Time Management')")}}
          coaching/remove-from-question-group!)

(defn- respond-to-event [team-id channel user-id text]
  (let [[command & args] (parser/parse-command text)
        {:keys [ef]} (@events (@event-aliases (str/lower-case command)))]
    (log/debugf "Responding to %s / %s" command args)
    (if ef
      (ef team-id channel user-id args)
      (do (log/errorf "Unexpected command: %s" command)
          "Unhandled command"))))

(defn- process-event [{:keys [team_id api_app_id
                              type authed_users]
                       {user-id :user
                        :keys [text ts channel event_ts]
                        event_type :type
                        event_subtype :subtype} :event
                       :as event}]
  (let [[access-token bot-access-token]
        (storage/get-access-tokens (db/datasource) team_id)

        {:keys [email]} (slack/get-user-info access-token user-id)]
    (ss/try+
      (if-not (storage/is-bot-user? (db/datasource) team_id user-id)
        (try
          (when (slack/is-im-to-me? bot-access-token channel)
            (respond-to-event team_id channel user-id text))
          (finally (coaching/event-occurred! team_id email)))
        "Ignoring message from myself.")
      (catch [:type :coachbot.command-parser/parse-failure] {:keys [result]}
        (handle-parse-failure text result)
        (coaching/submit-text! team_id email text))
      (catch Exception t (handle-unknown-failure t event)))))

(defn make-queue-if-configured []
  (when (env/event-queue-enabled?)
    (log/infof "Event queue size: %d" @env/event-queue-size)
    (let [q (LinkedBlockingQueue. (int @env/event-queue-size))
          e (Executors/newFixedThreadPool 1)]
      (.submit e (cast Callable
                       #(while true
                          (try
                            (let [evt (.take q)]
                              (log/debugf "Received event: %s" evt)
                              (process-event evt))
                            (catch Throwable t
                              (log/error t "Unable to process event"))))))
      q)))

(def ^:private event-queue (delay (make-queue-if-configured)))

(defn handle-event [{:keys [token] :as event}]
  (when-not (= token @env/slack-verification-token)
    (ss/throw+ {:type ::access-denied}))

  (if (env/event-queue-enabled?)
    (if (.offer @event-queue event)
      (do
        (log/debugf "Queue depth %d" (.size @event-queue))
        "submitted")
      (ss/throw+ {:type ::queue-full}))
    (process-event event)))

(defroutes event-routes
  (GET "/oauth" []
    :query-params [code :- String]
    :summary "Give Slack our authorization code so we can be helpful!"
    (if (slack/auth-slack code auth-success)
      (ok "Application authorized!")
      (unauthorized)))

  (POST "/event" []
    :body [message EventMessage]
    :summary "Receive an event from Slack"
    (log/infof "Message received: %s" message)
    (ss/try+ (let [result
                   (ok (if-let [challenge-response
                                (slack/challenge-response message)]

                         challenge-response
                         {:result (handle-event message)}))]
               (log/debugf "event result: %s" result)
               result)
             (catch [:type ::access-denied] _ (unauthorized))
             (catch [:type ::queue-full] _ (service-unavailable))))

  (POST "/message" []
    :form-params [payload :- s/Any]
    :summary "Receive a message from a button from Slack"
    (log/infof "Message received: payload=%n%s" payload)
    (let [message (json/parse-string payload)]
      (log/infof "Parsed: %s" (with-out-str (pprint/pprint message))))
    (ok)))