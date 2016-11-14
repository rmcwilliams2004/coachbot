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
  (:require [coachbot.db :as db]
            [coachbot.env :as env]
            [coachbot.events :as events]
            [coachbot.handler :refer :all]
            [coachbot.slack :as slack]
            [coachbot.storage :as storage]
            [clojure.data.json :as json]
            [clojure.java.jdbc :as jdbc]
            [ring.mock.request :as mock]
            [speclj.core :refer :all]
            [taoensso.timbre :as log]))

;todo Kill this evil hack.
(log/set-level! :error)

(def access-token "gobbledygook")
(def bot-access-token "bot_stuff!!@!$sc$AG$A$^AVASEA$")
(def user-id "abc123")
(def team-id "def456")
(def team-name "The Best Team Ever")
(def bot-user-id "bot999")

(describe "detailed event handling"
  (with-all ds (db/make-db-datasource "h2" "jdbc:h2:mem:test" "" ""))
  (before-all (storage/store-slack-auth! @ds {:team-id team-id
                                              :team-name "test team"
                                              :access-token "test123"
                                              :user-id "test456"
                                              :bot-access-token "test789"
                                              :bot-user-id bot-user-id}))
  (after-all (jdbc/execute! @ds ["drop all objects"]))

  (with messages (atom []))

  (around [it] (with-redefs [env/datasource
                             (fn [] @ds)

                             slack/send-message!
                             (fn [_ _ msg] (swap! @messages conj msg))

                             events/handle-unknown-failure
                             (fn [t _] (swap! @messages conj (str t)))

                             events/handle-parse-failure
                             (fn [t _]
                               (swap! @messages conj
                                      (format "Failed to parse: %s" t)))]
                 (it)))

  ;; Note: the "hi" command is covered in the handler-spec

  (context "help"
    (it "responds to help command properly"
      (do
        (events/handle-event {:token "none" :team_id team-id
                              :event {:text "help"}})
        (should= [(str "Here are the commands I respond to:\n"
                       " • hi -- checks if I'm listening\n"
                       " • help -- display this help message")] @@messages)))))
