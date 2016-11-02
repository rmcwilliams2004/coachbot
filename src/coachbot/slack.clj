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

(ns coachbot.slack
  (:require [cheshire.core :as cheshire]
            [clj-http.client :as client]
            [clojure.walk :as walk]
            [coachbot.env :as env]
            [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [taoensso.timbre :as log]))

(defn- params [param-map]
  (merge {:client_id @env/slack-client-id
          :client_secret @env/slack-client-secret}
         param-map))

(defn- get [url & {:as param-map}]
  (client/get url (:query-params (params param-map))))

(defn auth-slack [code]
  (let [result
        (get "https://slack.com/api/oauth.access" :code code)
        {:keys [ok access_token user_id team_name team_id]
         {:keys [bot_user_id bot_access_token]} :bot
         :as body}
        (-> result
            :body
            cheshire/parse-string
            walk/keywordize-keys)]
    (if ok
      (let [_ (log/infof "Authorization successful. Body: %s" body)

            user-list
            (get "https://slack.com/api/users.list" :token access_token)]
        (log/infof "User list response: %s" user-list))
      (log/errorf "Authorization failed. Body: %s" body))
    ok))

