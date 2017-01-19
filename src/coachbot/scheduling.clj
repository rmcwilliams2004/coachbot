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

(ns coachbot.scheduling
  (:require [clojurewerkz.quartzite.jobs :as qj]
            [taoensso.timbre :as log]
            [clojurewerkz.quartzite.triggers :as qt]
            [clojurewerkz.quartzite.schedule.cron :as qc]
            [clojurewerkz.quartzite.scheduler :as qs]))

(defmacro defsfn
  "Define a function that runs a function on a schedule when executed"
  [fname schedule f]
  (let [fname-str (name fname)
        unable-to-execute (format "Unable to execute job '%s'" fname-str)
        job-key (str "jobs." fname-str)
        trigger-key (str "triggers." fname-str)]
    `(do
       (qj/defjob job# [ctx]
         (try
           (~f)
           (catch Throwable t#
             (log/error t# ~unable-to-execute))))

       (defn ~fname [scheduler#]
         (let [job# (qj/build
                      (qj/of-type job#)
                      (qj/with-identity (qj/key ~job-key)))
               trigger# (qt/build
                          (qt/with-identity (qt/key ~trigger-key))
                          (qt/start-now)
                          (qt/with-schedule
                            (qc/schedule (qc/cron-schedule ~schedule))))]
           (qs/schedule scheduler# job# trigger#))))))