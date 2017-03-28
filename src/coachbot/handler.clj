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

(ns coachbot.handler
  (:require [clojure.pprint :as pprint]
            [clojurewerkz.quartzite.scheduler :as qs]
            [coachbot.channel-coaching-process :as ccp]
            [coachbot.coaching-process :as coaching]
            [coachbot.env :as env]
            [coachbot.events :as events]
            [coachbot.scheduling :as sch]
            [compojure.api.sweet :refer :all]
            [compojure.route :as r]
            [org.httpkit.server :as srv]
            [ring.util.http-response :refer :all]
            [schema.core :as s]
            [taoensso.timbre :as log])
  (:gen-class)
  (:import (java.io PipedInputStream PipedOutputStream)
           (java.util.concurrent Executors)))

(defn wrap-log-requests [handler]
  (fn [req]
    (when @env/log-requests?
      (log/with-level :debug
        (log/debugf "Request Details: %s"
                    (with-out-str (pprint/pprint req)))))
    (handler req)))

(defn wrap-dir-index [handler]
  (fn [req]
    (handler
      (update-in req [:uri]
                 #(if (= "/" %) "/index.html" %)))))

(def ^:private chart-streamers (Executors/newCachedThreadPool))

(defn get-channel-chart-stream [id]
  (let [in (PipedInputStream.)
        out (PipedOutputStream. in)]
    (.submit chart-streamers
             (cast Callable
                   #(try
                      (ccp/render-plot-for-channel-question! id out)
                      (catch Throwable t
                        (log/error t "Unable to render chart"))
                      (finally (.close out)))))

    in))

(defapi app
  {:swagger
   {:ui "/swagger-ui"
    :spec "/swagger.json"
    :data {:info {:title "CoachBot by Courage Labs, LLC"
                  :description "Simple, elegant, automatic motivation"}
           :tags [{:name "api", :description "CoachBot APIs"}]}}}

  (middleware [wrap-dir-index wrap-log-requests]
    (context "/api/v1" []
      :tags ["APIs"]
      events/event-routes)

    (GET "/.well-known/acme-challenge/:challenge" []
      :path-params [challenge :- String]
      (if (= @env/letsencrypt-challenge challenge)
        (content-type (ok @env/letsencrypt-challenge-response) "text/plain")
        (not-found)))

    (undocumented
      (GET ccp/channel-chart-url-in-pattern []
        :path-params [id :- String]
        (ok (get-channel-chart-stream id)))
      (r/resources "/"))))

(defn do-individual-coaching! []
  (coaching/send-next-question-to-everyone-everywhere!)
  (coaching/deliver-scheduled-custom-questions!))

(sch/every-minute schedule-individual-coaching! "Individual Coaching"
                  do-individual-coaching!)

(sch/every-minute schedule-channel-coaching! "Channel Coaching"
                  ccp/send-results-for-all-channel-questions!)

(sch/every-minute schedule-delayed-messages! "Delayed Messages"
                  ccp/deliver-delayed-messages!)

(defn -main
  "Main function. Invoked to run the application using httpkit."
  []
  (let [log-config {:level @env/log-level}
        log-config (if-not @env/log-other-libs
                     (assoc log-config :ns-whitelist ["coachbot.*"])
                     log-config)
        _ (log/merge-config! log-config)
        port @env/port
        scheduler (qs/start (qs/initialize))]

    (log/info "Starting scheduled jobs")
    (schedule-individual-coaching! scheduler)
    (schedule-channel-coaching! scheduler)
    (schedule-delayed-messages! scheduler)

    (log/infof "Getting ready to listen on port %d" port)
    (srv/run-server app {:port port})))