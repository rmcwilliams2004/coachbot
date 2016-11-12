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
            [coachbot.command-parser :as parser]
            [coachbot.env :as env]
            [coachbot.slack :as slack]
            [coachbot.storage :as storage]
            [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [schema.core :as s]
            [taoensso.timbre :as log])
  (:use [slingshot.slingshot :only [throw+ try+]]))

(s/defschema EventMessage
  {s/Any s/Any})

(defn- auth-success [& {:keys [access-token bot-access-token] :as auth-data}]
  (storage/store-slack-auth! (env/datasource) auth-data)
  (let [members (slack/list-members access-token)]
    (doseq [{:keys [id first-name]} members]
      ; don't overrun the slack servers
      (Thread/sleep 500)

      (slack/send-message! bot-access-token id
                           (format "Hello, %s." first-name)))))

(defn- is-bot-user? [team-id user]
  (storage/is-bot-user? (env/datasource) team-id user))

(defn hello-world [team_id channel user-id]
  (let [[access-token bot-access-token]
        (storage/get-access-tokens (env/datasource) team_id)
        {:keys [first-name]} (slack/get-user-info access-token user-id)]
    (slack/send-message! bot-access-token channel (str "Hello, " first-name))))

(defn- handle-unknown-failure [t event]
  (log/errorf t "Unable to handle event: %s" event)
  "Unknown failure")

(defn- handle-parse-failure [text result]
  (log/errorf "Unable to parse command: %s" text)
  (log/debugf "Parse Result: %s" result)
  "Unparseable command")

(defn handle-event [{:keys [token team_id api_app_id
                            type authed_users]
                     {:keys [user text ts channel event_ts]
                      event_type :type} :event
                     :as event}]
  (when-not (= token @env/slack-verification-token)
    (throw+ {:type ::access-denied}))

  (try+
    (if-not (is-bot-user? team_id user)
      (let [[command] (parser/parse-command text)]
        (case (str/lower-case command)
          "hi" (hello-world team_id channel user)
          (do
            (log/errorf "Unexpected command: %s" command)
            "Unhandled command"))))
    (catch [:type :coachbot.command-parser/parse-failure] {:keys [result]}
      (handle-parse-failure text result))
    (catch Exception t (handle-unknown-failure t event))))

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
    (try+ (ok
           (if-let [challenge-response (slack/challenge-response message)]
             challenge-response
             {:result (handle-event message)}))
         (catch [:type ::access-denied] _
           (unauthorized)))))