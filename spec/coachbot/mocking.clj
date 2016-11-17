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
  (:require [coachbot.events :as events]
            [coachbot.slack :as slack]
            [coachbot.env :as env]))

(defn mock-event-boundary [messages ds it]
  (with-redefs
    [env/datasource (fn [] ds)
     slack/send-message! (fn [_ _ msg] (swap! messages conj msg))
     slack/get-user-info (fn [_ _] {:first-name "Bill"})
     events/handle-unknown-failure (fn [t _] (swap! messages conj (str t)))
     events/handle-parse-failure (fn [t _]
                                   (swap! messages conj
                                          (format "Failed to parse: %s" t)))]
    (it)))