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
  (:require [coachbot.slack :as slack]
            [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [taoensso.timbre :as log]
            [schema.core :as s]))

(s/defschema EventMessage
  {s/Any s/Any})

(defn- auth-success [& {:keys [access-token bot-access-token] :as auth-data}]
  (let [members (slack/list-members access-token)]
    (doseq [{:keys [id first-name]} members]
      ; don't overrun the slack servers
      (Thread/sleep 500)

      (slack/send-message bot-access-token id
                          (format "Hello, %s." first-name)))))

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
    (ok (when (:challenge message) (select-keys message [:challenge])))))