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

(def access-token "gobbledygook")
(def bot-access-token "bot_stuff!!@!$sc$AG$A$^AVASEA$")
(def team-name "The Best Team Ever")
(def bot-user-id "bot999")

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
  (before-all (storage/store-slack-auth! @ds
                                         {:team-id team-id
                                          :team-name team-name
                                          :access-token access-token
                                          :user-id user0-id
                                          :bot-access-token bot-access-token
                                          :bot-user-id bot-user-id}))
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

    (before-all (storage/replace-base-questions! @ds ["first question"
                                                      "second question"
                                                      "third question"
                                                      "fourth question"
                                                      "fifth question"
                                                      "sixth question"])

                (hi-from-everyone)
                (handle-event user1-id "next question")
                (handle-event user2-id "start coaching")
                (hi-from-everyone)

                ;; should be ignored since it's in a channel, not an IM
                (handle-event user1-id "general" "hi")
                (handle-event user1-id "start coaching")
                (handle-event user1-id "some fun answer")
                (handle-event user2-id "another fun answer")
                (handle-event user2-id "stop coaching")
                (coaching/send-questions! team-id)
                (handle-event user1-id "some confused answer")
                (handle-event user1-id "stop coaching")
                (storage/reset-all-coaching-users! @ds)
                (handle-event user1-id "start coaching")

                ;; re-send same if not answered
                (coaching/send-questions! team-id)
                (handle-event user1-id "some fun answer")
                (handle-event user1-id "stop coaching")
                (handle-event user1-id "next question")

                ;; send new one even if previous not answered
                (handle-event user1-id "next question")
                (coaching/send-questions! team-id))

    (it "starts and stops coaching for users properly"
      (should=
        [hello-user1
         hello-user2
         (str user1-id ": first question")
         (str user2-id ": Thanks! We'll start sending you messages soon.")
         (str user2-id ": first question")
         hello-user1
         hello-user2
         (str user1-id ": Thanks! We'll start sending you messages soon.")
         (str user2-id ": No problem! We'll stop sending messages.")
         (str user1-id ": second question")
         (str user1-id ": No problem! We'll stop sending messages.")
         (str user1-id ": Thanks! We'll start sending you messages soon.")
         (str user1-id ": third question")
         (str user1-id ": third question")
         (str user1-id ": No problem! We'll stop sending messages.")
         (str user1-id ": fourth question")
         (str user1-id ": fifth question")]
        @@messages)

      (should= [{:question "first question"}
                {:question "second question"}
                {:question "third question"}
                {:question "third question"}
                {:question "fourth question"}
                {:question "fifth question"}]
               (storage/list-questions-asked @ds team-id user1-email))

      (should= [{:question "first question", :answer "some fun answer"}
                {:question "second question", :answer "some confused answer"}
                {:question "third question", :answer "some fun answer"}]
               (storage/list-answers @ds team-id user1-email))

      (should= [{:question "first question"}]
               (storage/list-questions-asked @ds team-id user2-email))

      (should= [{:question "first question", :answer "another fun answer"}]
               (storage/list-answers @ds team-id user2-email)))))