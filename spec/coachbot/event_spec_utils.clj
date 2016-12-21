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

(ns coachbot.event-spec-utils
  (:require [coachbot.events :as events]))

(defn handle-event
  ([team-id user-id channel text]
   (events/handle-raw-event {:token "none" :team_id team-id
                             :event {:text text :user user-id
                                     :channel channel}}))
  ([team-id user-id text]
   (handle-event team-id user-id user-id text)))