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

(ns coachbot.mocking
  (:require [coachbot.events :as events]
            [coachbot.messages :as messages]
            [coachbot.slack :as slack]
            [coachbot.db :as db]
            [taoensso.timbre :as log]))

(def access-token "gobbledygook")
(def bot-access-token "bot_stuff!!@!$sc$AG$A$^AVASEA$")
(def team-id "def456")
(def team-name "The Best Team Ever")
(def bot-user-id "bot999")
(def channel-id "D2X6TCYJE")

(def user0-id "abc123")
(def user1-id "blah")
(def user3-id "simple")

(def user1-email "blah@there.com")
(def user2-email "meh@here.com")

(def user1-first-name "Bill")
(def user2-first-name "Cathy")

(def user1 {:team-id team-id :id user1-id
            :email user1-email :timezone "America/Chicago"
            :real-name "bblah" :first-name user1-first-name :last-name "Blah"
            :name "Bill Blah"})

(def user2-id "meh")
(def user2 {:team-id team-id :id user2-id
            :email user2-email :timezone "America/Chicago"
            :real-name "cmeh" :first-name user2-first-name :last-name "Meh"
            :name "Cathy Meh"})

(def user3 {:team-id team-id :id user3-id
            :email "suser@simple.com" :timezone "America/Chicago"
            :first-name nil :last-name nil
            :real-name "Simple User" :name "suser"})

(def users {user0-id {:first-name "Bill"}
            user1-id user1
            user2-id user2
            user3-id user3})

(defn uc [user-id content] (str user-id ": " content))
(def u1c (partial uc user1-id))
(def u2c (partial uc user2-id))

(def u1-thanks-for-answer (u1c messages/thanks-for-answer))
(def u2-thanks-for-answer (u2c messages/thanks-for-answer))

(def u1-coaching-hello (u1c messages/coaching-hello))
(def u2-coaching-hello (u2c messages/coaching-hello))

(def u1-coaching-goodbye (u1c messages/coaching-goodbye))
(def u2-coaching-goodbye (u2c messages/coaching-goodbye))

(def slack-auth {:team-id team-id
                 :team-name team-name
                 :access-token access-token
                 :user-id user0-id
                 :bot-access-token bot-access-token
                 :bot-user-id bot-user-id})

(defn mock-event-boundary [messages ds it]
  (with-redefs
    [db/datasource (fn [] ds)
     slack/send-message! (fn [_ channel msg]
                           (swap! messages conj (str channel ": " msg)))
     slack/get-user-info (fn [_ user-id] (users user-id))
     slack/is-im-to-me? (fn [_ channel] (contains? users channel))
     events/handle-unknown-failure
     (fn [t _]
       (log/error t)
       (swap! messages conj (.getMessage t)))]
    (it)))