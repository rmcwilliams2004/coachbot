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
  (:require [clojurewerkz.quartzite.scheduler :as qs]
            [coachbot.channel-coaching-process :as ccp]
            [coachbot.coaching-process :as coaching]
            [coachbot.env :as env]
            [coachbot.events :as events]
            [coachbot.scheduling :as sch]
            [compojure.api.sweet :refer :all]
            [compojure.route :as r]
            [org.httpkit.server :as srv]
            [ring.util.http-response :refer :all]
            [taoensso.timbre :as log])
  (:gen-class))

(defn wrap-dir-index [handler]
  (fn [req]
    (handler
      (update-in req [:uri]
                 #(if (= "/" %) "/index.html" %)))))

(defapi app
  {:swagger
   {:ui "/swagger-ui"
    :spec "/swagger.json"
    :data {:info {:title "CoachBot by Courage Labs, LLC"
                  :description "Simple, elegant, automatic motivation"}
           :tags [{:name "api", :description "CoachBot APIs"}]}}}

  (middleware [wrap-dir-index]
    (context "/api/v1" []
      :tags ["APIs"]
      events/event-routes)

    (GET "/.well-known/acme-challenge/:challenge" []
      :path-params [challenge :- String]
      (if (= @env/letsencrypt-challenge challenge)
        (content-type (ok @env/letsencrypt-challenge-response) "text/plain")
        (not-found)))

    (undocumented (r/resources "/"))))

(sch/defsfn schedule-individual-coaching! "0 * * ? * *"
            coaching/send-next-question-to-everyone-everywhere!)

(sch/defsfn schedule-channel-coaching! "0 * * ? * *"
            ccp/send-results-for-all-channel-questions!)

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

    (log/infof "Getting ready to listen on port %d" port)
    (srv/run-server app {:port port})))