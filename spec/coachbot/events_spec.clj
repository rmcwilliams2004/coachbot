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

(ns coachbot.events-spec
  (:require [clojure.data.json :as json]
            [coachbot.handler :refer :all]
            [ring.mock.request :as mock]
            [speclj.core :refer :all]
            [clojure.java.io :as io]))

(describe "Events"
  (context "Oauth"
    (with response (app (-> (mock/request :get "/api/v1/oauth?code=test"))))
    (with body (:body @response))
    (with result-container (atom []))

    (it "Can GET OAuth request"
      (with-redefs [coachbot.slack/auth-slack
                    (fn [code _]
                      (swap! @result-container conj code)
                      true)]
        (should= 200 (:status @response))
        (should= "Application authorized!" @body)
        (should= ["test"] @@result-container)))

    (it "Responds poorly when the result is bad"
      (with-redefs [coachbot.slack/auth-slack (fn [code _] false)]
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