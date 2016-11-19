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
            [coachbot.slack :as slack]
            [coachbot.env :as env]
            [coachbot.coaching-process :as coaching]))

(def team-id "def456")

(def user0-id "abc123")
(def user1-id "blah")

(def user1-email "blah@there.com")
(def user2-email "meh@here.com")

(def user1 {:team-id team-id :remote-user-id user1-id
            :email user1-email :timezone "America/Chicago"
            :real-name "bblah" :first-name "Bill" :last-name "Blah"
            :name "Bill Blah"})

(def user2-id "meh")
(def user2 {:team-id team-id :remote-user-id user2-id
            :email user2-email :timezone "America/Chicago"
            :real-name "cmeh" :first-name "Cathy" :last-name "Meh"
            :name "Cathy Meh"})

(def users {user0-id {:first-name "Bill"}
            user1-id user1
            user2-id user2})

(defn mock-event-boundary [messages ds it]
  (with-redefs
    [env/datasource (fn [] ds)
     slack/send-message! (fn [_ channel msg]
                           (swap! messages conj (str channel ": " msg)))
     slack/get-user-info (fn [_ user-id] (users user-id))
     events/handle-unknown-failure (fn [t _] (swap! messages conj (str t)))]
    (it)))