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

(ns coachbot.channel-coaching-spec
  (:require [clj-time.core :as t]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [coachbot.events :as events]
            [coachbot.channel-coaching-process :refer :all]
            [coachbot.mocking :refer :all]
            [coachbot.storage :as storage]
            [speclj.core :refer :all]
            [taoensso.timbre :as log])
  (:import (clojure.lang ExceptionInfo)))

(log/set-level! :error)

(defn response [response-fmt text answer]
  (format (str "response: " response-fmt) answer text))

(def first-response
  (partial response "Thanks! I've got you down for *%d* for *%s*"))

(def next-response
  (partial response "Great! I've changed your answer to *%d* for *%s*"))

(def expired-response
  (partial response
           "Sorry, but I can't submit *%d* to *%s* because it's expired!"))

(defn set-event-channel-id [msg channel-id]
  (assoc-in msg [:event :channel] channel-id))

(defn load-edn [filename]
  (-> (io/resource (str "channel_coaching/" filename))
      slurp
      edn/read-string
      (assoc :token good-token)))

(defn load-event-edn [filename]
  (-> filename
      load-edn
      (assoc :team_id team-id)
      (set-event-channel-id channel-id)))

(defn button-pressed [question-id user-id value]
  (-> "option_chosen.edn"
      load-edn
      (assoc-in [:team :id] team-id)
      (assoc-in [:channel :id] channel-id)
      (assoc-in [:actions 0 :value] (str value))
      (assoc-in [:user :id] user-id)
      (update-in [:callback_id] #(format % question-id))))

(defn bob [msg] (set-event-channel-id msg "bob"))
(def group-join (load-event-edn "group_join.edn"))
(def group-leave (load-event-edn "group_leave.edn"))
(def channel-join (load-event-edn "channel_join.edn"))
(def channel-join-bob (bob channel-join))
(def channel-leave (load-event-edn "channel_leave.edn"))
(def channel-leave-bob (bob channel-leave))

(defn expected [question]
  (format "<!here|here> Do you agree with this statement? *%s*" question))
(def first-question "test question")
(def second-question "second question")
(def third-question "third question")
(def fourth-question "fourth question")

(def fq-expected (expected first-question))
(def sq-expected (expected second-question))
(def tq-expected (expected third-question))
(def fourthq-expected (expected fourth-question))

(def cmsg (partial uc channel-id))

(defn send-channel-question-results! [latest-messages]
  (do
    (send-results-for-all-channel-questions!)
    (latest-messages)))

(defmacro a-channel-event [name message expectation latest-messages]
  `(it ~name
     (should= {:status 200, :headers {}, :body nil}
              (events/handle-raw-event ~message))
     (should= ~expectation (~latest-messages))))

(defmacro should-be-in-channels [expected-channels events]
  (let [raw-events (map (fn [event] `(events/handle-raw-event ~event)) events)]
    `(should= ~expected-channels (do ~@raw-events (list-channels ~team-id)))))

(defmacro should-ask-question [question expected expected-expiration id
                               left-label right-label num-buttons
                               reversed? latest-messages & expiration-specs]
  (let [help-text
        (format "%d=%s, %d=%s%s. _Expires in %s._"
                (if reversed? num-buttons 1) left-label
                (if reversed? 1 num-buttons) right-label
                (if reversed? " (*Scale is reversed*)" "")
                expected-expiration)
        button-range (map #(hash-map :name "option" :value %)
                          (range 1 (inc num-buttons)))
        button-range (if reversed? (reverse button-range) button-range)]
    `(should= [{:msg (cmsg ~expected)
                :attachments
                [{:type :buttons
                  :callback-id (format "cquestion-%d" ~id)
                  :buttons [~@button-range]
                  :help-text ~help-text}]}]
              (do (send-channel-question! team-id channel-id
                                          ~left-label ~right-label
                                          ~num-buttons ~reversed?
                                          ~question
                                          ~@expiration-specs)
                  (~latest-messages)))))

(defmacro should-store-response [id answer qid email]
  `(should=
     {:id ~id, :answer ~answer}
     (storage/get-channel-question-response @ds team-id ~qid ~email)))

(defmacro check-expired-questions [latest-messages & expectations]
  `(it "should not accept answers"
     (should= [~@expectations]
              (do (events/handle-raw-event (button-pressed 1 user1-id 3))
                  (events/handle-raw-event (button-pressed 2 user1-id 3))
                  (~latest-messages)))))

(defn stats-response [channel question-text avg smax smin scount]
  {:msg (format "%s: Results from question: *%s*" channel question-text)
   :attachments [{:type :image
                  :description
                  (format (str/join "\n" ["Average: %.2f"
                                          "Max: %d"
                                          "Min: %d"
                                          "From %d people responding"])
                          avg smax smin scount)}]})

(def not-enough-response
  (partial format
           (str "%s: Question *%s* only had *%d* response(s). "
                "We don't display results unless we get at least *%d* "
                "because we care about your privacy.")))

(def stats-response2-3
  (stats-response channel-id second-question 3.0 4 2 3))
(def not-enough-response1-1
  (not-enough-response channel-id first-question 1 3))

(describe-mocked "Channel coaching" [ds latest-messages]
  (describe "Channel joins"
    (a-channel-event "should handle a group joining"
      group-join [(cmsg channel-coaching-message)] latest-messages)

    (a-channel-event "should handle a group leaving"
      group-leave [] latest-messages)

    (a-channel-event "should handle a channel joining"
      channel-join [(cmsg channel-coaching-message)] latest-messages)

    (a-channel-event "should handle a channel leaving"
      channel-leave [] latest-messages)

    (it "should handle multiple channels"
      (should-be-in-channels [] [])
      (should-be-in-channels [channel-id "bob"] [channel-join
                                                 channel-join-bob])
      (should-be-in-channels [channel-id] [channel-leave-bob])))

  (describe "Channel questions"
    (before-all (latest-messages))
    (with-all channels-coached (list-channels team-id))

    (it "should ask questions to the channel"
      (should-ask-question first-question fq-expected "1 day" 1
                           "Highly Inaccurate" "Highly Accurate" 5 false
                           latest-messages)
      (should-ask-question second-question sq-expected
                           "2 days, 1 hour" 2
                           "Highly Inaccurate" "Highly Accurate" 5 false
                           latest-messages
                           (t/days 2) (t/hours 1))
      (should-ask-question third-question tq-expected
                           "4 days, 2 hours" 3
                           "Never" "Always" 4 false
                           latest-messages
                           (t/days 4) (t/hours 2))
      (should-ask-question fourth-question fourthq-expected
                           "3 hours" 4
                           "Highly Inaccurate" "Highly Accurate" 5 true
                           latest-messages (t/hours 3)))

    (it "should accept answers"
      (should= [(first-response first-question 3)

                (first-response second-question 3)
                (next-response second-question 5)
                (first-response second-question 4)
                (first-response second-question 2)

                (first-response third-question 3)
                (first-response third-question 4)
                (first-response third-question 5)]
               (do (events/handle-raw-event (button-pressed 1 user1-id 3))

                   (events/handle-raw-event (button-pressed 2 user1-id 3))
                   (events/handle-raw-event (button-pressed 2 user1-id 5))
                   (events/handle-raw-event (button-pressed 2 user2-id 4))
                   (events/handle-raw-event (button-pressed 2 user3-id 2))

                   (events/handle-raw-event (button-pressed 3 user1-id 3))
                   (events/handle-raw-event (button-pressed 3 user2-id 4))
                   (events/handle-raw-event (button-pressed 3 user3-id 5))

                   (latest-messages)))
      (should-store-response 1 3 1 user1-email)
      (should-store-response 2 5 2 user1-email)
      (should-store-response 3 4 2 user2-email))

    (context "after expiration"
      (now-context "earlier" "2016-01-03T10:10:00-06:00"
        (check-expired-questions latest-messages
                                 (expired-response first-question 3)
                                 (next-response second-question 3)))

      (now-context "later" "2016-01-04T10:10:00-06:00"
        (with-all channel-question-results
          (send-channel-question-results! latest-messages))

        (with-all url-id
          (->> @channel-question-results
               second
               :attachments
               first
               :url
               (re-find #"^http://localhost:3000/charts/channel/(.*)$")
               second))

        (check-expired-questions latest-messages
                                 (expired-response first-question 3)
                                 (expired-response second-question 3))

        (it "should express the results of the questions in an aggregated way"
          (should= [not-enough-response1-1 stats-response2-3]
                   (update-in @channel-question-results
                              [1 :attachments 0]
                              dissoc :url))

          (should= [] (send-channel-question-results! latest-messages)))

        (it "should decrypt question ID properly"
          (should= 2 (decrypt-id @url-id)))
        (it "should not decrypt a question ID signed with the wrong key"
          (should-throw ExceptionInfo "Message seems corrupt or manipulated."
            (decrypt-id (encrypt-id "wrong" 2))))

        (now-context "much later" "2016-02-28T10:10:00-06:00"
          (it "should not decrypt a question ID that has expired"
            (should-throw ExceptionInfo "Token is expired (1452528600)"
              (decrypt-id @url-id))))))
    (it "should not decrypt gibberish"
      (should-throw ExceptionInfo "Message seems corrupt or manipulated."
        (decrypt-id "gibberish")))))