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

(ns coachbot.channel-coaching-process
  (:require [coachbot.coaching-process :as cp]
            [coachbot.db :as db]
            [coachbot.slack :as slack]
            [coachbot.storage :as storage]))

(def ^:private question-response-messages
  {:added "Thanks for your response!"
   :updated "Great! I've changed your response."})

(def channel-coaching-message
  (str "Hi everyone! I'm here to send periodic coaching questions. "
       "Just kick me out if you get sick of them."))

(defn coach-channel [slack-team-id channel _]
  (let [ds (db/datasource)]
    (storage/add-coaching-channel! ds slack-team-id channel)
    (storage/with-access-tokens ds slack-team-id [_ bot-access-token]
      (slack/send-message! bot-access-token channel
                           channel-coaching-message))))

(defn stop-coaching-channel [slack-team-id channel _]
  (storage/stop-coaching-channel! (db/datasource) slack-team-id channel))

(defn list-channels [team-id]
  (storage/list-coaching-channels (db/datasource) team-id))

(defn send-channel-question [slack-team-id channel msg]
  (cp/with-sending-constructs
    {:team-id slack-team-id :channel channel} [ds send-fn _]
    (let [question-id (storage/add-channel-question!
                        (db/datasource) slack-team-id channel msg)]
      (send-fn msg (format "cquestion-%s" question-id)
               (map #(hash-map :name "option" :value %) (range 1 6))))))

(defn send-channel-question-response [conn slack-team-id _ {:keys [email]}
                                      question-id _ value]

  (question-response-messages
    (storage/store-channel-question-response!
      conn slack-team-id email question-id (Integer/parseInt value))))