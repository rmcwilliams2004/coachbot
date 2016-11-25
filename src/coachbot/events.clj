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
  (:require [clojure.string :as str]
            [coachbot.coaching-process :as coaching]
            [coachbot.command-parser :as parser]
            [coachbot.env :as env]
            [coachbot.slack :as slack]
            [coachbot.storage :as storage]
            [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [schema.core :as s]
            [slingshot.slingshot :as ss]
            [taoensso.timbre :as log]))

(s/defschema EventMessage
  {s/Any s/Any})

(def ^:private events (atom {}))

(def ^:private event-aliases (atom {}))

(defn defevent [{:keys [command help aliases]} ef]
  (swap! events assoc command {:help help :ef ef})

  (let [aliases (or aliases [])]
    (doseq [alias (conj aliases command)]
      (swap! event-aliases assoc alias command))))

(defn- auth-success [& {:keys [access-token bot-access-token] :as auth-data}]
  (storage/store-slack-auth! (env/datasource) auth-data)
  (let [members (slack/list-members access-token)]
    (doseq [{:keys [id first-name]} members]
      ; don't overrun the slack servers
      (Thread/sleep 500)

      (slack/send-message!
        bot-access-token id
        (format (str "Hello, %s. I'm a coaching robot. To get started, you "
                     "can say 'start coaching', otherwise say 'help' to see "
                     "what commands I respond to.")
                first-name)))))

(defn- handle-unknown-failure [t event]
  (log/errorf t "Unable to handle event: %s" event)
  "Unknown failure")

(defn- handle-parse-failure [text result]
  (log/warnf "Unable to parse command: %s" text)
  (log/debugf "Parse Result: %s" result))

(defn- hello-world [team-id channel user-id]
  (let [[access-token bot-access-token]
        (storage/get-access-tokens (env/datasource) team-id)
        {:keys [first-name]} (slack/get-user-info access-token user-id)]
    (slack/send-message! bot-access-token channel (str "Hello, " first-name))))

(defn- help [team-id channel _]
  (let [[_ bot-access-token]
        (storage/get-access-tokens (env/datasource) team-id)
        body (str/join "\n"
                       (map #(let [[command {:keys [help]}] %]
                               (format " • %s -- %s" command help)) @events))]

    (slack/send-message!
      bot-access-token channel
      (str "Here are the commands I respond to:\n" body))))

(defevent {:command "hi"
           :help "checks if I'm listening"} hello-world)

(defevent {:command "help"
           :help "display this help message"} help)

(defevent {:command "start coaching"
           :help "send daily motivational questions"} coaching/start-coaching!)

(defevent {:command "stop coaching"
           :help "stop sending questions"} coaching/stop-coaching!)

(defevent {:command "next question"
           :help "ask a new question"
           :aliases ["another question"]} coaching/next-question!)

(defn- respond-to-event [team-id channel user-id text]
  (let [[command & args] (parser/parse-command text)
        {:keys [ef]} (@events (@event-aliases (str/lower-case command)))]
    (if ef
      (ef team-id channel user-id)
      (do (log/errorf "Unexpected command: %s" command)
          "Unhandled command"))))

(defn handle-event [{:keys [token team_id api_app_id
                            type authed_users]
                     {user-id :user
                      :keys [text ts channel event_ts]
                      event_type :type} :event
                     :as event}]
  (when-not (= token @env/slack-verification-token)
    (ss/throw+ {:type ::access-denied}))

  (let [[access-token bot-access-token]
        (storage/get-access-tokens (env/datasource) team_id)

        {:keys [email]} (slack/get-user-info access-token user-id)]
    (ss/try+
      (if-not (storage/is-bot-user? (env/datasource) team_id user-id)
        (try
          (when (slack/is-im-to-me? bot-access-token channel)
            (respond-to-event team_id channel user-id text))
          (finally (coaching/event-occurred! team_id email)))
        "Ignoring message from myself.")
      (catch [:type :coachbot.command-parser/parse-failure] {:keys [result]}
        (handle-parse-failure text result)
        (coaching/submit-text! team_id email text))
      (catch Exception t (handle-unknown-failure t event)))))

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
    (ss/try+ (ok (if-let [challenge-response (slack/challenge-response message)]
                   challenge-response
                   {:result (handle-event message)}))
             (catch [:type ::access-denied] _ (unauthorized)))))