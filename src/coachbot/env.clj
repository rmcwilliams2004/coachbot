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

(ns coachbot.env
  (:require [clj-time.local :as tl]))

(defn- env-or [env-key f]
  (let [val (System/getenv env-key)]
    (if-not val (f) val)))

(defn env
  ([env-key] (env-or env-key
                     #(throw (IllegalStateException.
                               (format "Environment variable missing: %s"
                                       env-key)))))
  ([env-key default-value] (env-or env-key #(identity default-value))))

(defn nilsafe-parse-int [str] (when str (Integer/parseInt str)))

(defmacro defenv [binding key & {:keys [default tfn] :as params}]
  (let [has-param? (partial contains? params)
        tfn (if (has-param? :tfn) tfn 'identity)
        env-args [key]
        env-args (if (has-param? :default) (conj env-args default) env-args)]
    `(def ~binding (delay (~tfn (env ~@env-args))))))

(defenv db-type "DB_TYPE" :default "h2")
(defenv db-url "DB_URL" :default "jdbc:h2:./cbdb")
(defenv db-user "DB_USER" :default "coachbot")
(defenv db-pass "DB_PASS" :default "coachbot")
(defenv db-timeout "DB_CONN_TIMEOUT" :tfn Integer/parseInt :default "10000")
(defenv db-max-conn "DB_MAX_CONN" :tfn Integer/parseInt :default "10")

(defenv slack-client-id "SLACK_CLIENT_ID" :default nil)

(defenv slack-client-secret "SLACK_CLIENT_SECRET" :default nil)

(defenv slack-verification-token "SLACK_VERIFICATION_TOKEN" :default "none")

(defenv port "PORT" :tfn Integer/parseInt :default "3000")

(defenv log-level "LOG_LEVEL" :tfn keyword :default "info")

(defenv log-other-libs "LOG_OTHER_LIBS" :tfn Boolean/parseBoolean
        :default "false")


(defenv event-queue-size "EVENT_QUEUE_SIZE" :tfn nilsafe-parse-int
        :default nil)

(defenv letsencrypt-challenge "LETSENCRYPT_CHALLENGE")
(defenv letsencrypt-challenge-response "LETSENCRYPT_CHALLENGE_RESPONSE")

(defn event-queue-enabled? [] @event-queue-size)
(defn now [] (tl/local-now))