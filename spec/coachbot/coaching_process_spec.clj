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
            [taoensso.timbre :as log]
            [clj-time.core :as t]))

;todo Kill this evil hack.
(log/set-level! :error)

(def first-question "first question")
(def second-question "second question")
(def third-question "third question")

(def you-like-fun? "you like fun?")
(def how-much? "how much?")

(describe "Custom questions"
  (with-all ds (db/make-db-datasource "h2" "jdbc:h2:mem:test" "" ""))
  (before-all (storage/store-slack-auth! @ds slack-auth))
  (after-all (jdbc/execute! @ds ["drop all objects"]))

  (with-all messages (atom []))

  (around-all [it] (mock-event-boundary @messages @ds it))

  (before-all (storage/replace-base-questions!
                @ds [first-question second-question third-question]))

  (context "basic"
    (before-all
      (start-coaching! team-id user1-id user1-id)
      (submit-text! team-id user1-email "banswer1")
      (register-custom-question! team-id user1-id you-like-fun?)
      (next-question! team-id user1-id user1-id)
      (submit-text! team-id user1-email "qanswer1")
      (submit-text! team-id user1-email "qanswer2")
      (register-custom-question! team-id user1-id how-much?)
      (next-question! team-id user1-id user1-id)
      (submit-text! team-id user1-email "qanswer3")
      (next-question! team-id user1-id user1-id)
      (submit-text! team-id user1-email "banswer2"))

    (it "should ask a custom question before the next one in the rotation"
      (should= [u1-coaching-hello
                (u1c first-question)
                u1-thanks-for-answer
                (u1c you-like-fun?)
                u1-thanks-for-answer
                u1-thanks-for-answer
                (u1c how-much?)
                u1-thanks-for-answer
                (u1c second-question)
                u1-thanks-for-answer]
               @@messages)

      (should= [{:question first-question, :answer "banswer1"}
                {:question you-like-fun?, :answer "qanswer1"}
                {:question you-like-fun?, :answer "qanswer2"}
                {:question how-much?, :answer "qanswer3"}
                {:question second-question, :answer "banswer2"}]
               (storage/list-answers @ds team-id user1-email))))

  (context "timing"
    (it "should only ask questions of folks if it is after their scheduled time"
      )))