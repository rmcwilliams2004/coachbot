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
  (:require [clojure.java.jdbc :as jdbc]
            [coachbot.coaching-process :as coaching]
            [coachbot.db :as db]
            [coachbot.events :as events]
            [coachbot.handler :refer :all]
            [coachbot.mocking :refer :all]
            [coachbot.storage :as storage]
            [speclj.core :refer :all]
            [taoensso.timbre :as log]))

;todo Kill this evil hack.
(log/set-level! :error)

(def first-question "first question")
(def second-question "second question")
(def third-question "third question")
(def fourth-question "fourth question")

(def some-fun-answer "some fun answer")
(def another-fun-answer "another fun answer")
(def some-confused-answer "some confused answer")

(def general "general")

(defn handle-event
  ([user-id channel text]
   (events/handle-event {:token "none" :team_id team-id
                         :event {:text text :user user-id :channel channel}}))
  ([user-id text]
   (handle-event user-id user-id text)))

(defn hi-from-everyone []
  (handle-event user1-id "hi")
  (handle-event user2-id "hi"))

(def hello-user1 (str user1-id ": Hello, " user1-first-name))
(def hello-user2 (str user2-id ": Hello, " user2-first-name))

(describe "detailed event handling"
  (with-all ds (db/make-db-datasource "h2" "jdbc:h2:mem:test" "" ""))
  (before-all (storage/store-slack-auth! @ds slack-auth))
  (after-all (jdbc/execute! @ds ["drop all objects"]))

  (with-all messages (atom []))

  (around-all [it] (mock-event-boundary @messages @ds it))

  ;; Note: the "hi" command is covered in the handler-spec

  (context "help"
    (before (handle-event user1-id "help"))

    (it "responds to help command properly"
      (should=
        [(str user1-id ": Here are the commands I respond to:\n"
              " • hi -- checks if I'm listening\n"
              " • help -- display this help message\n"
              " • start coaching -- send daily motivational questions\n"
              " • stop coaching -- stop sending questions\n"
              " • next question -- ask a new question")] @@messages)))

  (context "Start and stop coaching"
    (before-all (swap! @messages empty))

    (before-all (storage/replace-base-questions!
                  @ds [first-question second-question third-question
                       fourth-question])

                (hi-from-everyone)
                (handle-event user1-id events/next-question-cmd)
                (handle-event user2-id events/start-coaching-cmd)
                (hi-from-everyone)

                ;; should be ignored since it's in a channel, not an IM
                (handle-event user1-id general events/hi-cmd)
                (handle-event user1-id events/start-coaching-cmd)
                (handle-event user1-id some-fun-answer)
                (handle-event user2-id another-fun-answer)
                (handle-event user2-id events/stop-coaching-cmd)
                (coaching/send-questions! team-id)
                (handle-event user1-id some-confused-answer)
                (handle-event user1-id events/stop-coaching-cmd)
                (handle-event user1-id events/start-coaching-cmd)
                (storage/reset-all-coaching-users! @ds)
                (handle-event user1-id general events/hi-cmd)

                ;; re-send same if not answered
                (coaching/send-questions! team-id)
                (handle-event user1-id some-fun-answer)
                (handle-event user1-id events/stop-coaching-cmd)
                (handle-event user1-id events/next-question-cmd)

                ;; send new one even if previous not answered
                (handle-event user1-id events/another-question-cmd)
                (coaching/send-questions! team-id))

    (it "starts and stops coaching for users properly"
      (should=
        [hello-user1
         hello-user2
         (u1c first-question)
         u2-coaching-hello
         (u2c first-question)
         hello-user1
         hello-user2
         u1-coaching-hello
         u1-thanks-for-answer
         u2-thanks-for-answer
         u2-coaching-goodbye
         (u1c second-question)
         u1-thanks-for-answer
         u1-coaching-goodbye
         u1-coaching-hello
         (u1c third-question)
         (u1c third-question)
         u1-thanks-for-answer
         u1-coaching-goodbye
         (u1c fourth-question)
         (u1c first-question)]
        @@messages)

      (should= [{:question first-question}
                {:question second-question}
                {:question third-question}
                {:question third-question}
                {:question fourth-question}
                {:question first-question}]
               (storage/list-questions-asked @ds team-id user1-email))

      (should= [{:question first-question, :answer some-fun-answer}
                {:question second-question, :answer some-confused-answer}
                {:question third-question, :answer some-fun-answer}]
               (storage/list-answers @ds team-id user1-email))

      (should= [{:question first-question}]
               (storage/list-questions-asked @ds team-id user2-email))

      (should= [{:question first-question, :answer another-fun-answer}]
               (storage/list-answers @ds team-id user2-email)))))