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
            [coachbot.handler :refer :all]
            [coachbot.storage :refer :all]
            [clojure.data.json :as json]
            [clojure.java.jdbc :as jdbc]
            [ring.mock.request :as mock]
            [speclj.core :refer :all]
            [taoensso.timbre :as log]
            [coachbot.env :as env]))

;todo Kill this evil hack.
(log/set-level! :error)

(def access-token "gobbledygook")
(def bot-access-token "bot_stuff!!@!$sc$AG$A$^AVASEA$")
(def user-id "abc123")
(def team-id "def456")
(def team-name "The Best Team Ever")
(def bot-user-id "bot999")

(describe "Events"
  (context "Oauth"
    (with response (app (-> (mock/request :get "/api/v1/oauth?code=test"))))
    (with body (:body @response))

    (with-all ds (db/make-db-datasource "h2" "jdbc:h2:mem:test" "" ""))

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
                 (get-access-tokens @ds team-id))))

    (it "Responds poorly when the result is bad"
      (with-redefs [coachbot.slack/auth-slack (fn [_ _] false)]
        (should= 401 (:status @response)))))

  (context "Events"
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
      (should= {"challenge" "bob"} @body))))