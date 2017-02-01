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
  (:require [buddy.sign.jwt :as jwt]
            [buddy.core.hash :as jwth]
            [clj-time.core :as t]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
            [coachbot.coaching-process :as cp]
            [coachbot.db :as db]
            [coachbot.env :as env]
            [coachbot.slack :as slack]
            [coachbot.storage :as storage]
            [incanter.core :as ic]
            [incanter.charts :as ich]
            [incanter.stats :as is]
            [taoensso.timbre :as log]
            [slingshot.slingshot :as ss])
  (:import (org.joda.time.format PeriodFormatterBuilder)))

(def ^:private question-response-messages
  {:added "Thanks! I've got you down for *%d* for *%s*"
   :updated "Great! I've changed your answer to *%d* for *%s*"
   :expired "Sorry, but I can't submit *%d* to *%s* because it's expired!"})

(def msg-format "@here %s _(expires in %s)_")

(def channel-chart-url-pattern "/charts/channel/%s")
(def channel-chart-url-out-pattern
  (str @env/robot-url channel-chart-url-pattern))
(def channel-chart-url-in-pattern (format channel-chart-url-pattern ":id"))

(def period-formatter
  (.toFormatter
    (doto (PeriodFormatterBuilder.)
      (.appendDays) (.appendSuffix " day" " days")
      (.appendSeparator ", " ", ")
      (.appendHours) (.appendSuffix " hour" " hours"))))

(def channel-coaching-message
  (str "Hi everyone! I'm here to send periodic coaching questions. "
       "Just kick me out if you get sick of them."))

(defn coach-channel! [slack-team-id channel _]
  (let [ds (db/datasource)]
    (storage/with-access-tokens ds slack-team-id [access-token bot-access-token]
      (let [channel-name (slack/get-channel-name access-token channel)]
        (storage/add-coaching-channel! ds slack-team-id channel channel-name)
        (slack/send-message! bot-access-token channel
                             channel-coaching-message)))))

(defn stop-coaching-channel! [slack-team-id channel _]
  (storage/stop-coaching-channel! (db/datasource) slack-team-id channel))

(defn list-channels [team-id]
  (storage/list-coaching-channels (db/datasource) team-id))

(defn send-channel-question!
  "Send a question to a channel. Expiration specs are one or more of the
   clj-time functions for adding time to a date. They are added to env/now
   and submitted as the new expiration date for the question. If you don't
   specify any expiration-specs, it will expire in 1 day.

   e.g. (send-channel-question! id ch question (t/days 1) (t/hours 12))

   Would make the question expire 1.5 days after the current time."
  [slack-team-id channel question & expiration-specs]
  (let [expiration-specs
        (if (empty? expiration-specs) [(t/days 1)] expiration-specs)

        now (env/now)
        plus-now (partial t/plus now)
        expiration-timestamp (apply plus-now expiration-specs)

        time-diff (.toPeriod (t/interval now expiration-timestamp))]
    (cp/with-sending-constructs
      {:team-id slack-team-id :channel channel} [ds send-fn _]
      (let [question-id (storage/add-channel-question!
                          (db/datasource) slack-team-id channel question
                          expiration-timestamp)]
        (send-fn
          (format msg-format question (.print period-formatter time-diff))
          [{:type :buttons :callback-id (format "cquestion-%s" question-id)
            :buttons
            (map #(hash-map :name "option" :value %) (range 1 6))}])))))

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

(defn- calculate-stats-for-channel-question [{:keys [answers] :as question}]
  (merge
    question
    (reduce
      (fn [m [k f]] (assoc m k (f answers))) {}
      [[:mean is/mean] [:median is/median] [:result-max (partial apply max)]
       [:result-min (partial apply min)] [:stdev is/sd]
       [:result-count count]])))

(def ^:private channel-results-format "Results from question: *%s*")

(def ^:private channel-results-stats-format
  (str/join "\n" ["Average: %.2f"
                  "Max: %d"
                  "Min: %d"
                  "From %d people responding"]))

(def ^:private not-enough-results-format
  (str "Question *%s* only had *%d* response(s). "
       "We don't display results unless we get at least *%d* because we "
       "care about your privacy."))

(def ^:private min-results 3)

(defn encrypt-id
  ([question-id]
   (encrypt-id @env/jwt-encryption-key question-id))
  ([encryption-key question-id]
   (jwt/encrypt {:exp (t/plus (env/now) (t/weeks 1))
                 :id question-id} (jwth/sha256 encryption-key))))

(defn decrypt-id [v]
  (-> v
      (jwt/decrypt (jwth/sha256 @env/jwt-encryption-key) {:now (env/now)})
      :id))

(defn- send-results-if-possible! [ds questions]
  (doseq [{:keys [result-count mean result-min result-max question
                  team-id channel-id question-id]}
          questions]
    (let [encrypted-id (encrypt-id question-id)
          [message attachments]
          (if (>= result-count min-results)
            [(format channel-results-format question)
             [{:type :image
               :url (format channel-chart-url-out-pattern encrypted-id)
               :description (format channel-results-stats-format
                                    mean result-max result-min result-count)}]]
            [(format not-enough-results-format question result-count
                     min-results) nil])]
      (log/infof "Sending results for %s / %s / %s / '%s'"
                 team-id channel-id question-id question)
      (try
        (jdbc/with-db-transaction [conn ds]
          (storage/with-access-tokens conn team-id
            [access-token bot-access-token]
            (storage/question-results-delivered! conn question-id)
            (slack/send-message! bot-access-token channel-id
                                 message attachments)))
        (catch Throwable t
          (log/errorf t "Could not send results for %s / %s / %s / '%s'"
                      team-id channel-id question-id question))))))

(defn send-results-for-all-channel-questions! []
  (let [ds (db/datasource)]
    (->> (storage/list-expired-channel-questions ds)
         (map calculate-stats-for-channel-question)
         (send-results-if-possible! ds))))

(defn get-results-for-channel-questions! [& expiration-specs]
  (let [timestamp (apply (partial t/minus (env/now)) expiration-specs)]
    (let [ds (db/datasource)]
      (->> (storage/list-delivered-channel-questions ds timestamp)
           (map calculate-stats-for-channel-question)
           (map #(dissoc % :answers))))))

(defn render-plot-for-channel-question! [encrypted-question-id out-stream]
  (let [question-id (decrypt-id encrypted-question-id)
        _ (log/debugf "Rendering plot for channel question %d" question-id)
        ds (db/datasource)
        {:keys [answers]} (storage/get-channel-question-results ds question-id)
        data (ic/dataset [:answer] (map (partial hash-map :answer) answers))]
    (ic/with-data data
      (ic/save
        (ich/box-plot :answer :y-label "") out-stream))))