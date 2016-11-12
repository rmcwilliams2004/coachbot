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

(ns coachbot.storage-spec
  (:require [coachbot.storage :refer :all]
            [clojure.java.jdbc :as jdbc]
            [coachbot.db :as db]
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

(describe "Storing data for later use"
  (context "Slack teams"
    (with-all ds (db/make-db-datasource "h2" "jdbc:h2:mem:test" "" ""))

    (after-all (jdbc/execute! @ds ["drop all objects"]))

    (before-all (store-slack-auth
                  @ds {:access-token "oldat"
                       :bot-access-token "oldbat"
                       :user-id user-id
                       :team-id team-id
                       :team-name team-name
                       :bot-user-id bot-user-id})

                (store-slack-auth
                  @ds {:access-token access-token
                       :bot-access-token bot-access-token
                       :user-id user-id
                       :team-id team-id
                       :team-name team-name
                       :bot-user-id bot-user-id}))

    (before-all (store-slack-auth
                  @ds {:access-token "another one"
                       :bot-access-token "stuff"
                       :user-id "bits"
                       :team-id "some-dumb-team"
                       :team-name "some dumb team"
                       :bot-user-id "some-bot-id"}))

    (it "should have stored the slack auth stuff"
      (should= [access-token bot-access-token]
               (get-access-tokens @ds team-id)))))
