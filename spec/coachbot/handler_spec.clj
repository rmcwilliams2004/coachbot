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
            [coachbot.slack :as slack]
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

(describe "Events"
  (with-all ds (db/make-db-datasource "h2" "jdbc:h2:mem:test" "" ""))

  (context "Oauth"
    (with response (app (-> (mock/request :get "/api/v1/oauth?code=test"))))
    (with body (:body @response))
    (after-all (jdbc/execute! @ds ["drop all objects"]))

    (it "Can GET OAuth request"
      (with-redefs [coachbot.slack/get-slack-auth
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
      (with response (app (-> (mock/request :post
                                            "/api/v1/event")
                              (mock/body
                                (json/write-str {:challenge "bob"
                                                 :token "abc"
                                                 :type "url_verification"}))
                              (mock/content-type "application/json"))))
      (with body (json/read-str (slurp (:body @response))))
      (it "Responds to a challenge with the challenge phrase"
        (should= 200 (:status @response))
        (should= {"challenge" "bob"} @body)))

    (context "Hello, World"
      (with msg-from-bot {:token "92N8CeL4ZrPSLBjReXwg9Vhz", :team_id
                          "T2T062KK4", :api_app_id "A2R05RSQ3",
                          :event {:type "message", :user bot-user-id,
                                  :text "Hello, CoachBot.", :bot_id "B2X6M65DM",
                                  :ts "1478969450.268969", :channel "D2X65Q02Y",
                                  :event_ts "1478969450.268969"},
                          :type "event_callback", :authed_users ["U2X4SN7H9"]})
      (with msg-from-user
            {:token "92N8CeL4ZrPSLBjReXwg9Vhz", :team_id "T2T062KK4",
             :api_app_id "A2R05RSQ3",
             :event {:type "message", :user "U2T161336", :text "hi",
                     :ts "1478967753.000006", :channel "D2X6TCYJE",
                     :event_ts "1478967753.000006"},
             :type "event_callback", :authed_users ["U2X4SN7H9"]})

      (with response (app (-> (mock/request :post
                                            "/api/v1/event")
                              (mock/body
                                (json/write-str @msg-from-user))
                              (mock/content-type "application/json"))))
      (with bot-response (app (-> (mock/request :post
                                                "/api/v1/event")
                                  (mock/body
                                    (json/write-str @msg-from-bot))
                                  (mock/content-type "application/json"))))
      (with messages (atom []))

      (it "Ignores bot users"
        (with-redefs [env/datasource (fn [] nil)
                      storage/get-bot-user-id (fn [_ _] bot-user-id)

                      storage/get-access-tokens
                      (fn [_ _] [access-token bot-access-token])]
          (should= 200 (:status @bot-response))))

      (it "Handles the 'hi' event"
        (with-redefs [env/datasource (fn [] nil)
                      storage/get-bot-user-id (fn [_ _] bot-user-id)

                      storage/get-access-tokens
                      (fn [_ _] [access-token bot-access-token])

                      slack/get-user-info
                      (fn [_ _] {:first-name "Bill"})

                      slack/send-message!
                      (fn [_ _ msg] (swap! @messages conj msg))]
          (should= 200 (:status @response))
          (should= ["Hello, Bill"] @@messages))))))