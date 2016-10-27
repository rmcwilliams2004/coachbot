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
  (:require [compojure.api.sweet :refer :all]
            [compojure.core :as cc]
            [compojure.route :as r]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(defn wrap-dir-index [handler]
  (fn [req]
    (handler
      (update-in req [:uri]
                 #(if (= "/" %) "/index.html" %)))))

(s/defschema Pizza
  {:name s/Str
   (s/optional-key :description) s/Str
   :size (s/enum :L :M :S)
   :origin {:country (s/enum :FI :PO)
            :city s/Str}})

(def app
  (api
    {:swagger
     {:ui "/swagger-ui"
      :spec "/swagger.json"
      :data {:info {:title "CoachBot by Courage Labs, LLC"
                    :description "Simple, elegant, automatic motivation"}
             :tags [{:name "api", :description "CoachBot APIs"}]}}}

    (middleware [wrap-dir-index]
      (context "/api/v1" []
        :tags ["APIs"]

        (GET "/plus" []
          :return {:result Long}
          :query-params [x :- Long, y :- Long]
          :summary "adds two numbers together"
          (ok {:result (+ x y)}))

        (POST "/echo" []
          :return Pizza
          :body [pizza Pizza]
          :summary "echoes a Pizza"
          (ok pizza)))

      (undocumented (r/resources "/"))

      (undocumented
        (cc/GET (str "/.well-known/acme-challenge/"
                     "nIP1gsj9yBW05FOLx8TXxal0HsnrIv9hfbRiCVyVxWo") []
          (ok (str "nIP1gsj9yBW05FOLx8TXxal0HsnrIv9hfbRiCVyVxWo.g6QAiw8SpNP"
                   "pxhMk9osyvfJoM3skZlmzD3qxEna4sgg")))))))
