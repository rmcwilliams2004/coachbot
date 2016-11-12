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

(ns coachbot.handler
  (:require [coachbot.env :as env]
            [coachbot.events :as events]
            [compojure.api.sweet :refer :all]
            [compojure.core :as cc]
            [compojure.route :as r]
            [org.httpkit.server :as srv]
            [ring.util.http-response :refer :all]
            [schema.core :as s]
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

    (undocumented (r/resources "/"))

    (undocumented
      (cc/GET (str "/.well-known/acme-challenge/"
                   "dZKR113j9JugTDth1wM-T9XhMbnY42GNLKIKfNXqsbU") []
        (content-type
          (ok (str "dZKR113j9JugTDth1wM-T9XhMbnY42GNLKIKfNXqsbU."
                   "g6QAiw8SpNPpxhMk9osyvfJoM3skZlmzD3qxEna4sgg"))
          "text/plain")))))

(defn -main
  "Main function. Invoked to run the application using httpkit."
  []
  (log/set-level! :info)
  (let [port @env/port]
    (log/infof "Getting ready to listen on port %d" port)
    (srv/run-server app {:port port})))