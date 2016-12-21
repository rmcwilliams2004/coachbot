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

(ns coachbot.question-groups-spec
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
            [coachbot.db :as db]
            [coachbot.event-spec-utils :refer :all]
            [coachbot.events :as events]
            [coachbot.mocking :refer :all]
            [coachbot.storage :as storage]
            [speclj.core :refer :all]
            [taoensso.timbre :as log]))

(def question1 "first q")
(def question2 "second q")
(def question3 "third q")
(def question4 "fourth q")

(def groupb "Groupb")
(def groupc "Groupc")
(def groupz "Groupz")

(describe "Question Groups"
  (with-all ds (db/make-db-datasource "h2" "jdbc:h2:mem:test" "" ""))
  (before-all (storage/store-slack-auth! @ds slack-auth)
              (storage/replace-base-questions-with-groups!
                @ds
                [{:question question1 :groups [groupc]}
                 {:question question2 :groups [groupz]}
                 {:question question3 :groups [groupz groupb]}
                 {:question question4}]))
  (after-all (jdbc/execute! @ds ["drop all objects"]))

  (with-all messages (atom []))

  (with-all clear-messages #(swap! @messages empty))

  (with-all single-event
    (fn [& strings]
      (do (swap! @messages empty)
          (handle-event team-id user1-id (str/join " " strings)) @@messages)))

  (with-all add-group #(@single-event events/add-to-group-cmd %))
  (with-all remove-group #(@single-event events/remove-from-group-cmd %))

  (with-all four-questions
    #(dotimes [_ 4] (handle-event team-id user1-id events/next-question-cmd)))

  (around-all [it] (mock-event-boundary @messages @ds it))

  (it "gives me a list of available groups"
    (should= [(u1c "The following groups are available:\n\n"
                   groupb "\n"
                   groupc "\n"
                   groupz "\n\n"
                   "You are in: no groups. You get all the questions!")]
             (@single-event events/show-question-groups-cmd)))

  (it "adds a group to be coached on"
    (should= [(u1c "I'll send you questions from " groupb "\n\n"
                   "You are in: " groupb)]
             (@add-group groupb))
    (should= [(u1c "Congrats. You're already a member of " groupb)]
             (@add-group groupb))
    (should= [(u1c "broke does not exist.")] (@add-group "broke"))
    (should= [(u1c "I'll send you questions from " groupz "\n\n"
                   "You are in: " groupb ", " groupz)]
             (@add-group groupz)))

  (it "shows me the groups I'm being coached on"
    (should= [(u1c "The following groups are available:\n\n"
                   groupb "\n"
                   groupc "\n"
                   groupz "\n\n"
                   (format "You are in: %s, %s" groupb groupz))]
             (@single-event events/show-question-groups-cmd)))

  (it "removes a group to be coached on"
    (should= [(u1c "Ok. I'll stop sending you questions from " groupz "\n\n"
                   "You are in: " groupb)]
             (@remove-group groupz))
    (should= [(u1c "No worries; you're not in " groupz)]
             (@remove-group groupz))
    (should= [(u1c "Ok. I'll stop sending you questions from " groupb "\n\n"
                   "You are in: no groups. You get all the questions!")]
             (@remove-group groupb)))

  (it "only sends me questions from the groups I'm being coached on"
    (should= [(u1c question3) (u1c question3) (u1c question3) (u1c question3)]
             (do
               (@add-group groupb)
               (@clear-messages)
               (@four-questions)
               @@messages))
    (should= [(u1c question1) (u1c question3) (u1c question1) (u1c question3)]
             (do (@add-group groupc) (@clear-messages) (@four-questions)
                 @@messages))
    (should= [(u1c question1) (u1c question2) (u1c question3) (u1c question1)]
             (do (@add-group groupz) (@clear-messages) (@four-questions)
                 @@messages))
    (should= [(u1c question2) (u1c question3) (u1c question4) (u1c question1)]
             (do
               (doseq [g [groupz groupb groupc]] (@remove-group g))
               (@clear-messages) (@four-questions) @@messages))))