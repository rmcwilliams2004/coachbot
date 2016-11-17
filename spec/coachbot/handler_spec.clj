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

(ns coachbot.handler-spec
  (:require [coachbot.db :as db]
            [coachbot.env :as env]
            [coachbot.handler :refer :all]
            [coachbot.mocking :refer :all]
            [coachbot.storage :as storage]
            [clojure.data.json :as json]
            [clojure.java.jdbc :as jdbc]
            [ring.mock.request :as mock]
            [speclj.core :refer :all]
            [taoensso.timbre :as log]))

;todo Kill this evil hack.
(log/set-level! :error)

(def access-token "gobbledygook")
(def bot-access-token "bot_stuff!!@!$sc$AG$A$^AVASEA$")
(def user-id "abc123")
(def team-id "def456")
(def team-name "The Best Team Ever")
(def bot-user-id "bot999")

(defn- message [& {:keys [event] :as msg}]
  (let [base-event {:type "message", :user "U2T161336", :text "hi",
                    :ts "1478967753.000006", :channel "D2X6TCYJE",
                    :event_ts "1478967753.000006"}
        result (merge {:token "none", :team_id team-id,
                       :api_app_id "A2R05RSQ3",
                       :type "event_callback", :authed_users ["U2X4SN7H9"]}
                      (dissoc msg :event))
        new-event (merge base-event event)]
    (assoc result :event new-event)))

(defn- send-event [req]
  (app (-> (mock/request :post
                         "/api/v1/event")
           (mock/body (json/write-str req))
           (mock/content-type "application/json"))))

(describe "Events"
  (with-all ds (db/make-db-datasource "h2" "jdbc:h2:mem:test" "" ""))

  (context "Oauth"
    (with response (app (-> (mock/request :get "/api/v1/oauth?code=test"))))
    (with body (:body @response))
    (after-all (jdbc/execute! @ds ["drop all objects"]))

    (it "Can GET OAuth request"
      (with-redefs [coachbot.slack/list-members
                    (fn [_] nil)

                    coachbot.slack/get-slack-auth
                    (fn [_]
                      {:ok true
                       :team_id team-id
                       :team_name team-name
                       :user_id user-id
                       :access_token access-token
                       :bot {:bot_access_token bot-access-token
                             :bot_user_id bot-user-id}})

                    env/datasource (fn [] @ds)]
        (should= 200 (:status @response))
        (should= "Application authorized!" @body)
        (should= [access-token bot-access-token]
                 (storage/get-access-tokens @ds team-id))))

    (it "Responds poorly when the result is bad"
      (with-redefs [coachbot.slack/auth-slack (fn [_ _] false)]
        (should= 401 (:status @response)))))

  (context "Events"
    (context "Challenge"
      (with response (send-event {:challenge "bob"
                                  :token "abc"
                                  :type "url_verification"}))
      (with body (json/read-str (slurp (:body @response))))

      (it "Responds to a challenge with the challenge phrase"
        (should= 200 (:status @response))
        (should= {"challenge" "bob"} @body)))

    ;; Note: the events-spec refers to this spec when explaining to devs why
    ;; there is no coverage for the "hi" command there.
    (context "Hello, World"
      (with-all ds (db/make-db-datasource "h2" "jdbc:h2:mem:test" "" ""))
      (before-all (storage/store-slack-auth! @ds {:team-id team-id
                                                  :team-name "test team"
                                                  :access-token "test123"
                                                  :user-id "test456"
                                                  :bot-access-token "test789"
                                                  :bot-user-id bot-user-id}))
      (after-all (jdbc/execute! @ds ["drop all objects"]))

      (with messages (atom []))

      (around [it] (mock-event-boundary @messages @ds it))

      (it "Ignores bot users"
        (should= 200 (:status
                       (send-event (message :event {:text "Hello, CoachBot."
                                                    :user bot-user-id}))))
        (should= [] @@messages))

      (it "Handles the 'hi' event"
        (should= 200 (:status (send-event (message))))
        (should= ["Hello, Bill"] @@messages))

      (it "Handles bad events"
        (should= 200 (:status (send-event (message :event {:text "sup"}))))
        (should= ["Failed to parse: sup"] @@messages))

      (it "Disallows bad activation tokens"
        (should= 401 (:status (send-event (message :token "bad"))))))))