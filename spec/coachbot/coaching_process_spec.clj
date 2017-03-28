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
  (:require [clojure.string :as str]
            [coachbot.coaching-process :refer :all]
            [coachbot.event-spec-utils :refer :all]
            [coachbot.mocking :refer :all]
            [coachbot.storage :as storage]
            [speclj.core :refer :all]))

(def first-question "first question")
(def second-question "second question")
(def third-question "third question")

(def you-like-fun? "you like fun?")
(def not-liked "you won't like this question")
(def dont-answer "THIS SHOULDN'T BE ANSWERED!")
(def how-much? "how much?")
(def how-many-toes? "How many toes do you have?")

(def qmsg (comp u1c (partial q-with-md "Custom Question")))
(def u1-not-liked (qmsg not-liked))
(def u1-you-like-fun? (qmsg you-like-fun?))
(def u1-dont-answer (qmsg dont-answer))
(def u1-how-much? (qmsg how-much?))
(def u1-how-many-toes? (qmsg how-many-toes?))

(defmacro should-show-for-user1 [latest-messages show-stmt & expected]
  `(should= [(uc user1-id "Here you go: \n" (str/join "\n" [~@expected]))]
            (do (handle-event team-id user1-id ~show-stmt)
                (~latest-messages))))

(describe-mocked "Custom questions" [ds latest-messages]
  (before-all
    (storage/replace-base-questions!
      @ds [first-question second-question third-question]))

  (context "basic"
    (before-all
      (with-now "2015-12-18T10:10:00-06:00"
        (start-coaching! team-id user1-id)
        (send-next-question-to-everyone-everywhere!))
      (submit-text! team-id user1-email "banswer1")
      (register-custom-question! team-id user1-id not-liked)
      (register-custom-question! team-id user1-id you-like-fun?)
      (next-question! team-id user1-id user1-id)
      (next-question! team-id user1-id user1-id)
      (submit-text! team-id user1-email "qanswer1")
      (submit-text! team-id user1-email "qanswer2")
      (register-custom-question! team-id user1-id dont-answer)
      (next-question! team-id user1-id user1-id)
      (register-custom-question! team-id user1-id how-much?)
      (next-question! team-id user1-id user1-id)
      (submit-text! team-id user1-email "qanswer3")
      (next-question! team-id user1-id user1-id)
      (submit-text! team-id user1-email "banswer2"))

    (it "should ask a custom question before the next one in the rotation"
      (should= [(u1c first-question)
                u1-thanks-for-answer
                u1-not-liked
                u1-you-like-fun?
                u1-thanks-for-answer
                u1-thanks-for-answer
                u1-dont-answer
                u1-how-much?
                u1-thanks-for-answer
                (u1c second-question)
                u1-thanks-for-answer]
               (latest-messages))

      (should= [{:question first-question, :answer "banswer1"}
                {:question you-like-fun?, :answer "qanswer1"}
                {:question you-like-fun?, :answer "qanswer2"}
                {:question how-much?, :answer "qanswer3"}
                {:question second-question, :answer "banswer2"}]
               (storage/list-answers @ds team-id user1-email)))

    (context "show questions"
      (it "should show the last questions"
        (should-show-for-user1 latest-messages "show last"
          second-question)

        (should-show-for-user1 latest-messages "show last 3 questions"
          second-question
          how-much?
          dont-answer)

        (should-show-for-user1 latest-messages "show last day"
          second-question
          how-much?
          dont-answer
          you-like-fun?
          not-liked)

        (should-show-for-user1 latest-messages "show last 2 weeks"
          second-question
          how-much?
          dont-answer
          you-like-fun?
          not-liked
          first-question)

        (should= [(u2c "Sadly, I haven't asked you any questions yet!")]
                 (do (handle-event team-id user2-id "show last 2 weeks")
                     (latest-messages))))))

  (context "scheduled"
    (before-all
      (with-now "2015-12-19T09:10:00-06:00"
        (schedule-custom-question! team-id user1-id "0 0 11 * * *"
                                   how-many-toes?))
      (with-now "2015-12-19T10:10:00-06:00"
        (deliver-scheduled-custom-questions!))
      (with-now "2015-12-19T11:05:00-06:00"
        (deliver-scheduled-custom-questions!))
      (with-now "2015-12-20T11:05:00-06:00"
        (deliver-scheduled-custom-questions!))
      (with-now "2015-12-20T12:05:00-06:00"
        (deliver-scheduled-custom-questions!)))

    (it "should ask the right number of question questions"
      (should= [u1-how-many-toes? u1-how-many-toes?]
               (latest-messages)))))