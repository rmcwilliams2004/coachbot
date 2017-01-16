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
  (:require [clj-time.format :as tf]
            [clojure.java.jdbc :as jdbc]
            [coachbot.env :as env]
            [coachbot.events :as events]
            [coachbot.messages :as messages]
            [coachbot.slack :as slack]
            [coachbot.storage :as storage]
            [coachbot.db :as db]
            [speclj.core :refer :all]
            [taoensso.timbre :as log]))

(def access-token "gobbledygook")
(def bot-access-token "bot_stuff!!@!$sc$AG$A$^AVASEA$")
(def team-id "def456")
(def team-name "The Best Team Ever")
(def bot-user-id "U395UPLDC")
(def channel-id "D2X6TCYJE")

(def user0-id "abc123")
(def user1-id "A1B235U01")
(def user3-id "DE1443U03")

(def user1-email "blah@there.com")
(def user2-email "meh@here.com")
(def user3-email "suser@simple.com")

(def user1-first-name "Bill")
(def user2-first-name "Cathy")

(def user1 {:team-id team-id :id user1-id
            :email user1-email :timezone "America/Chicago"
            :real-name "bblah" :first-name user1-first-name :last-name "Blah"
            :name "Bill Blah"})

(def user2-id "A1BCDEU02")
(def user2 {:team-id team-id :id user2-id
            :email user2-email :timezone "America/Chicago"
            :real-name "cmeh" :first-name user2-first-name :last-name "Meh"
            :name "Cathy Meh"})

(def user3 {:team-id team-id :id user3-id
            :email user3-email :timezone "America/Los_Angeles"
            :first-name nil :last-name nil
            :real-name "Simple User" :name "suser"})

(def users {user0-id {:team-id team-id :id user0-id
                      :email "bill@bill.bil" :timezone "Antarctica/Palmer"
                      :real-name "Bill Nye" :first-name "Bill"
                      :name "bnye"}
            user1-id user1
            user2-id user2
            user3-id user3})

(defn uc [user-id & content] (apply str (into [user-id ": "] content)))
(defn q-with-md [metadata question]
  (format "[_%s_] %s" metadata question))
(def u1c (partial uc user1-id))
(def u2c (partial uc user2-id))

(def u1-thanks-for-answer (u1c messages/thanks-for-answer))
(def u1-unknown (u1c messages/unknown-command))
(def u2-thanks-for-answer (u2c messages/thanks-for-answer))

(def coaching-hello (format messages/coaching-hello "10am"))
(def u1-coaching-hello (u1c coaching-hello))
(def u2-coaching-hello (u2c coaching-hello))

(def u1-coaching-goodbye (u1c messages/coaching-goodbye))
(def u2-coaching-goodbye (u2c messages/coaching-goodbye))

(def good-token "good")

(def slack-auth {:team-id team-id
                 :team-name team-name
                 :access-token access-token
                 :user-id user0-id
                 :bot-access-token bot-access-token
                 :bot-user-id bot-user-id})

(defn mock-event-boundary [messages ds it]
  (with-redefs
    [env/now (fn [] (tf/parse (tf/formatters :date-time-no-ms)
                              "2016-01-01T10:10:00-06:00"))
     db/datasource (fn [] ds)
     slack/send-message! (fn [_ channel msg & [callback-id buttons]]
                           (swap! messages conj
                                  (let [out-msg (str channel ": " msg)]
                                    (if callback-id
                                      {:msg out-msg
                                       :cid callback-id
                                       :btns buttons}
                                      out-msg))))
     slack/send-response! (fn [_ message] (swap! messages conj
                                                 (str "response: " message)))
     slack/get-user-info (fn [_ user-id] (users user-id))
     slack/is-im-to-me? (fn [_ channel] (contains? users channel))
     events/is-event-authorized? (fn [token] (= token good-token))
     events/handle-unknown-failure
     (fn [t _]
       (log/error t)
       (swap! messages conj (.getMessage t)))]
    (it)))

(defmacro describe-with-level [level name & body]
  `(describe "*"
     (around-all [it#] (log/with-level ~level (it#)))
     (context ~name ~@body)))

(defmacro with-clean-db [bindings & body]
  `(context "-"
     (with-all
       ~(first bindings)
       (db/make-db-datasource "h2" "jdbc:h2:mem:test" "" ""))

     (before-all (storage/store-slack-auth! (deref ~(first bindings))
                                            slack-auth))
     (after-all (jdbc/execute! (deref ~(first bindings))
                  ["drop all objects"]))
     ~@body))

(defmacro describe-mocked [name bindings & body]
  `(let [messages# (atom [])
         ~(second bindings) #(let [msgs# @messages#]
                               (swap! messages# empty)
                               msgs#)]
     (describe-with-level :error ~name
       (with-clean-db [~(first bindings)]
         (before-all (storage/store-slack-auth! (deref ~(first bindings))
                                                slack-auth))

         (around-all [it#]
           (mock-event-boundary messages# (deref ~(first bindings)) it#))

         ~@body))))