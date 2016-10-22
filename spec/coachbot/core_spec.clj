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
   (:require [cheshire.core :as cheshire]
             [coachbot.handler :refer :all]
             [ring.mock.request :as mock]
             [speclj.core :refer :all]))

(defn parse-body [body]
  (cheshire/parse-string (slurp body) true))

 (describe "Hello, World!"
   (with-all response (app (-> (mock/request :get  "/api/v1/plus?x=1&y=2"))))
   (with-all body     (parse-body (:body @response)))

   (it "Can GET request to /hello?name={a-name} and receive expected response"
     (should= 200 (:status @response))
     (should= 3 (:result @body))))
