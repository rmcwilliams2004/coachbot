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

(def db-type (env "DB_TYPE" "h2"))
(def db-url (env "DB_URL" "jdbc:h2:./cbdb"))
(def db-user (env "DB_USER" "coachbot"))
(def db-pass (env "DB_PASS" "coachbot"))
(def db-timeout (Integer/parseInt (env "DB_CONN_TIMEOUT" "10000")))
(def db-max-conn (Integer/parseInt (env "DB_MAX_CONN" "10")))

(def slack-client-id (delay (env "SLACK_CLIENT_ID" nil)))

(def slack-client-secret (delay (env "SLACK_CLIENT_SECRET" nil)))

(def slack-verification-token (delay (env "SLACK_VERIFICATION_TOKEN" "none")))

(def port (delay (Integer/parseInt (env "PORT" "3000"))))

(def log-level (delay (keyword (env "LOG_LEVEL" "info"))))

(def log-other-libs
  (delay (Boolean/parseBoolean (env "LOG_OTHER_LIBS" "false"))))

(def event-queue-size
  (delay (let [s (env "EVENT_QUEUE_SIZE" nil)] (when s (Integer/parseInt s)))))

(defn event-queue-enabled? []
  @event-queue-size)

(defn now [] (tl/local-now))
