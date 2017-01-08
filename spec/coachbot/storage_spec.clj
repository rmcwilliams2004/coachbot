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
  (:require [coachbot.mocking :refer :all]
            [coachbot.storage :as storage]
            [speclj.core :refer :all]))

(def another-access-token "another_one")
(def another-bot-access-token "stuff")
(def another-team-id "some-dumb-team")

(def first-team {:access-token access-token
                 :bot-access-token bot-access-token
                 :user-id user1-id
                 :team-id team-id
                 :team-name team-name
                 :bot-user-id bot-user-id})

(defn extra-fields [user]
  (assoc user :answered-qid nil :asked-qid nil :asked-cqid nil
              :last-question-date nil :active true))

(defn get-coaching-user [ds team-id email]
  (dissoc (storage/get-coaching-user ds team-id email)
          :created-date :updated-date))

(describe-mocked "Storing data for later use" [ds _]
  (context "Slack teams"
    (before-all (storage/store-slack-auth!
                  @ds {:access-token "oldat"
                       :bot-access-token "oldbat"
                       :user-id user1-id
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
    (before-all (storage/store-slack-auth! @ds first-team)
                (storage/add-coaching-user! @ds user1)
                (storage/add-coaching-user!
                  @ds (assoc user3 :coaching-time "wrong"))
                (storage/add-coaching-user! @ds user3))

    (it "should have stored the slack auth stuff"
      (should= (-> user1 extra-fields (assoc :coaching-time "0 0 10 * * *"))
               (get-coaching-user @ds team-id user1-email))
      (should= (-> user3 extra-fields (assoc :coaching-time "wrong"))
               (get-coaching-user @ds team-id user3-email)))

    (it "should come back with no users if nil email sent"
      (should-not (get-coaching-user @ds team-id nil)))))