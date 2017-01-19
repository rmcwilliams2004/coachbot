;
; Copyright (c) 2017, Courage Labs, LLC.
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
  (:require [clj-time.core :as t]
            [coachbot.coaching-process :as cp]
            [coachbot.db :as db]
            [coachbot.env :as env]
            [coachbot.slack :as slack]
            [coachbot.storage :as storage])
  (:import (org.joda.time.format PeriodFormatterBuilder)))

(def ^:private question-response-messages
  {:added "Thanks! I've got you down for *%d* for *%s*"
   :updated "Great! I've changed your answer to *%d* for *%s*"
   :expired "Sorry, but I can't submit *%d* to *%s* because it's expired!"})

(def msg-format "%s _(expires in %s)_")

(def period-formatter
  (.toFormatter
    (doto (PeriodFormatterBuilder.)
      (.appendDays) (.appendSuffix " day" " days")
      (.appendSeparator ", " ", ")
      (.appendHours) (.appendSuffix " hour" " hours"))))

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

(defn send-channel-question!
  "Send a question to a channel. Expiration specs are one or more of the
   clj-time functions for adding time to a date. They are added to env/now
   and submitted as the new expiration date for the question. If you don't
   specify any expiration-specs, it will expire in 1 day.

   e.g. (send-channel-question! id ch msg (t/days 1) (t/hours 12))

   Would make the question expire 1.5 days after the current time."
  [slack-team-id channel msg & expiration-specs]
  (let [expiration-specs
        (if (empty? expiration-specs) [(t/days 1)] expiration-specs)

        now (env/now)
        plus-now (partial t/plus now)
        expiration-timestamp (apply plus-now expiration-specs)

        time-diff (.toPeriod (t/interval now expiration-timestamp))]
    (cp/with-sending-constructs
      {:team-id slack-team-id :channel channel} [ds send-fn _]
      (let [question-id (storage/add-channel-question!
                          (db/datasource) slack-team-id channel msg
                          expiration-timestamp)]
        (send-fn
          (format msg-format msg (.print period-formatter time-diff))
          (format "cquestion-%s" question-id)
          (map #(hash-map :name "option" :value %) (range 1 6)))))))

(defn send-channel-question-response! [conn slack-team-id _ {:keys [email]}
                                       question-id _ value]
  (let [value (Integer/parseInt value)

        question
        (storage/get-channel-question-text conn question-id)

        response-format
        (question-response-messages
          (storage/store-channel-question-response!
            conn slack-team-id email question-id value))]
    (format response-format value question)))