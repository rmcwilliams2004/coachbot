;
; Copyright (c) 2017, Courage Labs, LLC.
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
  (:require [coachbot.coaching-process :refer :all]
            [coachbot.event-spec-utils :refer :all]
            [coachbot.mocking :refer :all]
            [coachbot.storage :as storage]
            [speclj.core :refer :all]
            [coachbot.events :as events]))

(def first-question "first question")
(def second-question "second question")
(def third-question "third question")

(def you-like-fun? "you like fun?")
(def not-liked "you won't like this question")
(def how-much? "how much?")

(def qmsg (partial q-with-md "Custom Question"))
(def u1-not-liked (qmsg not-liked))
(def u1-you-like-fun? (qmsg you-like-fun?))
(def u1-how-much? (qmsg how-much?))

(def qa-button (partial button-pressed "qasked"))

(describe-mocked "Custom questions" [ds latest-messages]
  (before-all
    (storage/replace-base-questions!
      @ds [first-question second-question third-question]))
  (context "basic"
    (before-all
      (start-coaching! team-id user1-id)
      (send-next-question-to-everyone-everywhere!)
      (submit-text! team-id user1-email "banswer1")
      (register-custom-question! team-id user1-id not-liked)
      (register-custom-question! team-id user1-id you-like-fun?)
      (next-question! team-id user1-id user1-id)
      (next-question! team-id user1-id user1-id)
      (submit-text! team-id user1-email "qanswer1")
      (submit-text! team-id user1-email "qanswer2")
      (register-custom-question! team-id user1-id how-much?)
      (next-question! team-id user1-id user1-id)
      (submit-text! team-id user1-email "qanswer3")
      (next-question! team-id user1-id user1-id)
      (submit-text! team-id user1-email "banswer2"))

    (it "should ask a custom question before the next one in the rotation"
      (should= [(qwb user1-id first-question 1)
                u1-thanks-for-answer
                (qwb user1-id u1-not-liked 2)
                (qwb user1-id u1-you-like-fun? 3)
                u1-thanks-for-answer
                u1-thanks-for-answer
                (qwb user1-id u1-how-much? 4)
                u1-thanks-for-answer
                (qwb user1-id second-question 5)
                u1-thanks-for-answer]
               (latest-messages))

      (should= [{:question first-question, :answer "banswer1"}
                {:question you-like-fun?, :answer "qanswer1"}
                {:question you-like-fun?, :answer "qanswer2"}
                {:question how-much?, :answer "qanswer3"}
                {:question second-question, :answer "banswer2"}]
               (storage/list-answers @ds team-id user1-email)))

    (it "should store ratings for questions"
      (should= [(u1c "Thanks for the rating!")]
               (do
                 (events/handle-raw-event (qa-button 1 user1-id 3))
                 (latest-messages)))
      (should= 3 (get-rating-for-question ds 1)))))