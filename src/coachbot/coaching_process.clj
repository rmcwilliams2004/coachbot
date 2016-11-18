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
            [coachbot.storage :as storage]
            [taoensso.timbre :as log]))

(defn start-coaching [team-id channel user-id]
  (let [[access-token bot-access-token]
        (storage/get-access-tokens (env/datasource) team-id)]
    (storage/add-coaching-user! (env/datasource)
                                (slack/get-user-info access-token user-id))
    (slack/send-message! bot-access-token channel
                         "Thanks! We'll start sending you messages soon.")))

(defn stop-coaching [team-id channel user-id]
  (let [[access-token bot-access-token]
        (storage/get-access-tokens (env/datasource)
                                   team-id)]
    (storage/remove-coaching-user! (env/datasource)
                                   (slack/get-user-info access-token user-id))
    (slack/send-message! bot-access-token channel
                         "No problem! We'll stop sending messages.")))

(defn new-question
  "Sends a new question to a specific individual."
  [ds user]
  (log/infof "new-question: %s" user)
  ;; get last question asked
  ;; if answered, choose new question, else re-send prior question
  )

(defn new-questions
  "Sends new questions to everyone on a given team that has signed up for
   coaching."
  [ds team-id]
  (doall (map (partial new-question ds)
              (storage/list-coaching-users ds team-id))))

(defn submit-text [team_id user text]
  ;; If there is an outstanding for the user, submit that
  ;; Otherwise store it someplace for a live person to review
  )