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

(ns coachbot.coaching-process-spec
  (:require [clojure.java.jdbc :as jdbc]
            [coachbot.coaching-process :refer :all]
            [coachbot.db :as db]
            [coachbot.mocking :refer :all]
            [coachbot.storage :as storage]
            [speclj.core :refer :all]
            [taoensso.timbre :as log]))

;todo Kill this evil hack.
(log/set-level! :error)

(describe "Custom questions"
  (with-all ds (db/make-db-datasource "h2" "jdbc:h2:mem:test" "" ""))
  (before-all (storage/store-slack-auth! @ds slack-auth))
  (after-all (jdbc/execute! @ds ["drop all objects"]))

  (with-all messages (atom []))

  (around-all [it] (mock-event-boundary @messages @ds it))

  (before-all (storage/replace-base-questions! @ds ["first question"
                                                    "second question"
                                                    "third question"]))

  (context "basic"
    (before-all
      (start-coaching! team-id user1-id user1-id)
      (next-question! team-id user1-id user1-id)
      (register-custom-question! team-id user1-id "you like fun?")
      (submit-text! team-id user1-email "answer")
      (next-question! team-id user1-id user1-id)
      (submit-text! team-id user1-email "answer")
      (next-question! team-id user1-id user1-id)
      (submit-text! team-id user1-email "answer"))

    (it "should ask a custom question before the next one in the rotation"
      #_(should= ["things"] @@messages))))