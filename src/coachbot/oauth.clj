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

(ns coachbot.oauth
  (:require [clj-http.client :as client]
            [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [schema.core :as s]
            [taoensso.timbre :as log]
            [coachbot.env :as env]))

(defn auth-slack [code]
  (log/info (client/get "https://slack.com/api/oauth.access"
                        {:query-params {:client_id @env/slack-client-id
                                        :client_secret @env/slack-client-secret
                                        :code code}})))

(defroutes oauth-routes
  (context "/oauth" []
    (GET "/" []
      :query-params [code :- String]
      :summary "Give Slack our authorization code so we can be helpful!"
      (auth-slack code)
      (ok "Application authorized!"))))