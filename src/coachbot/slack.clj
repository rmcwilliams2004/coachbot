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

(defn- get-url [url & {:as param-map}]
  (client/get url {:query-params (params param-map)}))

(defn- post-url [url & {:as param-map}]
  (client/post url {:form-params (params param-map)}))

(defn- parse-body [result]
  (-> result
      :body
      cheshire/parse-string
      walk/keywordize-keys))

(defn- list-members [access-token]
  (let [user-list-result
        (get-url "https://slack.com/api/users.list" :token access-token)

        {:keys [ok members] :as body}
        (parse-body user-list-result)]
    (if ok
      (->> members
           (filter #(not (:is_bot %)))
           (filter #(not (= "slackbot" (:name %))))
           (map
             #(let [{:keys [id name real_name tz_label]
                     {:keys [first_name email]}
                     :profile} %]
               {:id id :name name :real-name real_name
                :timezone tz_label :first-name first_name
                :email email})))
      (log/errorf "Unable to get user list: %s" body))))

(defn- send-message [access-token channel message]
  (log/info "Sending %s to %s" message channel)
  (post-url "https://slack.com/api/chat.postMessage"
            :token access-token
            :channel channel
            :text message
            :as_user true))

(defn auth-slack [code]
  (let [auth-result
        (get-url "https://slack.com/api/oauth.access" :code code)

        {:keys [ok access_token user_id team_name team_id]
         {:keys [bot_user_id bot_access_token]} :bot
         :as body}
        (parse-body auth-result)]
    (if ok
      (let [members (list-members access_token)]
        (log/infof "Authorization successful. Body: %s" body)
        (doseq [{:keys [id first-name]} members]
          ; don't overrun the slack servers
          (Thread/sleep 500)

          (send-message bot_access_token id
                        (format "Hello, %s." first-name))))
      (log/errorf "Authorization failed. Body: %s" body))
    ok))

