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

(ns coachbot.core-spec
  (:require [coachbot.handler :refer :all]
            [ring.mock.request :as mock]
            [speclj.core :refer :all]))

(describe "OAuth"
  (with-all response (app (-> (mock/request :get "/api/v1/oauth?code=test"))))
  (with-all body (:body @response))
  (with-all token-container (atom []))


  (it "Can GET OAuth request"
    (with-redefs [coachbot.slack/auth-slack
                  (fn [code] (swap! @token-container conj code))]
      (should= 200 (:status @response))
      (should= "Application authorized!" @body)
      (should= ["test"] @@token-container))))
