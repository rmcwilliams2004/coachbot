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
  (:require [coachbot.coaching-process :as coaching]
            [coachbot.handler :refer :all]
            [coachbot.mocking :refer :all]
            [coachbot.storage :as storage]
            [clojure.data.json :as json]
            [ring.mock.request :as mock]
            [speclj.core :refer :all]))

(def howdy-regex "(:?Hello, |Yo, |Howdy, |Hola, |How's it going )")
(def hello-regex
  (re-pattern (format "%s: %s(%s|%s)" user0-id howdy-regex
                   user1-first-name user3-name)))

(defn- hi-search [l]
  (every? (partial re-find hello-regex) l))

(defn- message [& {:keys [event] :as msg}]
  (let [base-event {:type "message", :user user0-id, :text "hi",
                    :ts "1478967753.000006", :channel user0-id,
                    :event_ts "1478967753.000006"}
        result (merge {:token good-token, :team_id team-id,
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

(describe-mocked "Events" [ds latest-messages]
  (context "Oauth"
    (with response (app (-> (mock/request :get "/api/v1/oauth?code=test"))))
    (with body (:body @response))

    (it "Can GET OAuth request"
      (with-redefs [coachbot.slack/list-members
                    (fn [_] nil)

                    coachbot.slack/get-slack-auth
                    (fn [_]
                      {:ok true
                       :team_id team-id
                       :team_name team-name
                       :user_id user0-id
                       :access_token access-token
                       :bot {:bot_access_token bot-access-token
                             :bot_user_id bot-user-id}})]
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
      (with messages (atom []))

      (around [it]
        (with-redefs
          [coaching/submit-text!
           (fn [_ _ t]
             (swap! @messages conj (format "Text submitted: %s" t)))]
          (mock-event-boundary @messages @ds it)))

      (it "Ignores bot users"
        (should= 200 (:status
                       (send-event (message :event {:text "Hello, CoachBot."
                                                    :user bot-user-id}))))
        (should= [] @@messages))

      (it "Handles the 'hi' event"
        (should= 200 (:status (send-event (message))))
        (should= 200 (:status (send-event (message :event {:user user3-id}))))
        (should-be hi-search @@messages))

      (it "Ignores the 'hi' event in a public channel"
        (should= 200 (:status (send-event (message :event {:channel
                                                           channel-id}))))
        (should= [] @@messages))

      (it "Handles bad events"
        (should= 200 (:status (send-event (message :event {:text "sup"}))))
        (should= ["Text submitted: sup"] @@messages))

      (it "Disallows bad activation tokens"
        (should= 401 (:status (send-event (message :token "bad"))))))))