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

(ns coachbot.command-parser)

(defn parse-command [regex command]
  (let [full (vec (re-seq regex command))
        full-vec? (vector? full)
        first-full (first full)
        first-full-vec? (vector? first-full)
        result (rest first-full)
        result-vec? (vector? result)]

    (if-not first-full-vec?
      (throw (IllegalArgumentException.
               (format
                 (str "Regular expression should contain at least one group "
                      "which is the command itself. %n"
                      "Regex: %s%n"
                      "Full result: %s (%s)%n"
                      "First: %s (%s) %n"
                      "Result: %s (%s)")
                 regex full full-vec?
                 first-full first-full-vec?
                 result result-vec?)))
      result)))