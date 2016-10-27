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

(ns coachbot.env)

(defn- env-or [env-key f]
  (let [val (System/getenv env-key)]
    (if-not val (f) val)))

(defn- env
  ([env-key] (env-or env-key
                     #(throw (IllegalStateException.
                               (format "Environment variable missing: %s"
                                       env-key)))))
  ([env-key default-value] (env-or env-key #(identity default-value))))

(def slack-token (env "COACHBOT_SLACK_TOKEN" nil))

(def port (Integer/parseInt (env "PORT" "3000")))