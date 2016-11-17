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

(ns coachbot.coaching-process
  (:require [coachbot.env :as env]
            [coachbot.slack :as slack]
            [coachbot.storage :as storage]))

(defn start-coaching [team-id channel user-id]
  (let [[access-token bot-access-token]
        (storage/get-access-tokens (env/datasource) team-id)]
    (storage/add-coaching-user! (env/datasource)
                                (slack/get-user-info access-token user-id))
    (slack/send-message! bot-access-token channel
                         "Thanks! We'll start sending you messages soon.")))

(defn stop-coaching [team-id channel user-id]
  (let [[_ bot-access-token] (storage/get-access-tokens (env/datasource)
                                                        team-id)]
    (slack/send-message! bot-access-token channel
                         "No problem! We'll stop sending messages.")))

(defn new-question
  "Sends a new question to a specific individual."
  [ds team-id user-id]
  )

(defn new-questions
  "Sends new questions to everyone on a given team that has signed up for
   coaching."
  [ds team-id]
  )