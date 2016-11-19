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
  (:require [coachbot.storage :as storage]
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

(def another-access-token "another_one")
(def another-bot-access-token "stuff")
(def another-team-id "some-dumb-team")

(def user1-id "A1B235678")
(def user2-id "A1BCDEFGH")

(def user1-email "stephen@couragelabs.com")
(def user2-email "travis.marsh@gmail.com")

(def first-team {:access-token access-token
                 :bot-access-token bot-access-token
                 :user-id user-id
                 :team-id team-id
                 :team-name team-name
                 :bot-user-id bot-user-id})

(def user1 {:id user1-id,
            :team-id team-id,
            :name "scstarkey",
            :real-name "Stephen Starkey",
            :timezone "America/Chicago",
            :first-name "Stephen",
            :last-name "Starkey",
            :email user1-email})

(def user2 {:id user2-id,
            :team-id team-id,
            :name "travis",
            :real-name "Travis Marsh",
            :timezone "America/Los_Angeles",
            :first-name "Travis",
            :last-name "Marsh",
            :email user2-email})

(defn extra-fields [user]
  (assoc user :answered-qid nil :asked-qid nil :days-since-question nil
              :active true))

(describe "Storing data for later use"
  (context "Slack teams"
    (with-all ds (db/make-db-datasource "h2" "jdbc:h2:mem:test" "" ""))

    (after-all (jdbc/execute! @ds ["drop all objects"]))

    (before-all (storage/store-slack-auth!
                  @ds {:access-token "oldat"
                       :bot-access-token "oldbat"
                       :user-id user-id
                       :team-id team-id
                       :team-name team-name
                       :bot-user-id bot-user-id})

                (storage/store-slack-auth! @ds first-team)

                (storage/store-slack-auth!
                  @ds {:access-token another-access-token
                       :bot-access-token another-bot-access-token
                       :user-id "bits"
                       :team-id another-team-id
                       :team-name "some dumb team"
                       :bot-user-id "some-bot-id"}))

    (it "should have stored the slack auth stuff"
      (should= [access-token bot-access-token]
               (storage/get-access-tokens @ds team-id))

      (should= [another-access-token another-bot-access-token]
               (storage/get-access-tokens @ds another-team-id))))

  (context "Slack coaching users"
    (with-all ds (db/make-db-datasource "h2" "jdbc:h2:mem:test" "" ""))

    (after-all (jdbc/execute! @ds ["drop all objects"]))

    (before-all (storage/store-slack-auth! @ds first-team)
                (storage/add-coaching-user! @ds user1)
                (storage/add-coaching-user! @ds user2))

    (it "should have stored the slack auth stuff"
      (should= (extra-fields user1)
               (storage/get-coaching-user @ds team-id user1-email))
      (should= (extra-fields user2)
               (storage/get-coaching-user @ds team-id user2-email)))))
