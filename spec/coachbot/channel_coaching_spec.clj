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

(ns coachbot.channel-coaching-spec
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.jdbc :as jdbc]
            [coachbot.events :as events]
            [coachbot.channel-coaching-process :refer :all]
            [coachbot.db :as db]
            [coachbot.mocking :refer :all]
            [coachbot.storage :as storage]
            [speclj.core :refer :all]
            [taoensso.timbre :as log]))

(log/set-level! :error)

(defn set-channel-id [msg channel-id]
  (assoc-in msg [:event :channel] channel-id))

(defn load-edn [filename]
  (-> (io/resource (str "channel_coaching/" filename))
      slurp
      edn/read-string
      (assoc :token good-token :team_id team-id)
      (set-channel-id channel-id)))

(defn bob [msg] (set-channel-id msg "bob"))
(def group-join (load-edn "group_join.edn"))
(def group-leave (load-edn "group_leave.edn"))
(def channel-join (load-edn "channel_join.edn"))
(def channel-join-bob (bob channel-join))
(def channel-leave (load-edn "channel_leave.edn"))
(def channel-leave-bob (bob channel-leave))

(def cmsg (partial uc channel-id))

(describe-mocked "Channel coaching" [ds latest-messages]
  (describe "Channel joins"
    (it "should handle a group joining"
      (should= {:status 200, :headers {}, :body nil}
               (events/handle-raw-event group-join))
      (should= [(cmsg channel-coaching-message)]
               (latest-messages)))

    (it "should handle a group leaving"
      (should= {:status 200, :headers {}, :body nil}
               (events/handle-raw-event group-leave))
      (should= [] (latest-messages)))

    (it "should handle a channel joining"
      (should= {:status 200, :headers {}, :body nil}
               (events/handle-raw-event channel-join))
      (should= [(cmsg channel-coaching-message)]
               (latest-messages)))

    (it "should handle a channel leaving"
      (should= {:status 200, :headers {}, :body nil}
               (events/handle-raw-event channel-leave))
      (should= [] (latest-messages)))

    (it "should handle multiple channels"
      (should= [] (list-channels team-id))
      (should= [channel-id "bob"]
               (do (events/handle-raw-event channel-join)
                   (events/handle-raw-event channel-join-bob)
                   (list-channels team-id)))
      (should= [channel-id]
               (do (events/handle-raw-event channel-leave-bob)
                   (list-channels team-id)))))

  (describe "Asking questions"
    (before-all (latest-messages))
    (with-all channels-coached (list-channels team-id))

    (it "should ask the question to the channel"
      (should= [{:msg (cmsg "test"), :cid "cquestion-1", :btns
                 [{:name "option", :value 1} {:name "option", :value 2}
                  {:name "option", :value 3} {:name "option", :value 4}
                  {:name "option", :value 5}]}]
               (do (send-channel-question team-id channel-id "test")
                   (latest-messages)))

      (should= [{:msg (cmsg "second"), :cid "cquestion-2", :btns
                 [{:name "option", :value 1} {:name "option", :value 2}
                  {:name "option", :value 3} {:name "option", :value 4}
                  {:name "option", :value 5}]}]
               (do (send-channel-question team-id channel-id "second")
                   (latest-messages))))

    (it "should accept answers")

    (it "should replace old answers")

    (it "should not accept answers outside of the acceptable range")

    (it "should not accept non-numeric answers")

    (it "should not accept answers after the question has expired")

    (it "should express the results of the questions in an aggregated way")))