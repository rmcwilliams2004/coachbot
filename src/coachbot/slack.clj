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

(ns coachbot.slack
  (:require [cheshire.core :as cheshire]
            [clj-http.client :as client]
            [clojure.walk :as walk]
            [coachbot.env :as env]
            [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [slingshot.slingshot :as ss]
            [taoensso.timbre :as log]))

(defn- params [param-map]
  (merge {:client_id @env/slack-client-id
          :client_secret @env/slack-client-secret}
         param-map))

(defn- get-url [url & {:as param-map}]
  (client/get url {:query-params (params param-map)}))

(defn- post-url! [url & {:keys [content-type] :as param-map}]
  (let [param-map (-> param-map
                      (dissoc :content-type)
                      params
                      (update-in [:attachments] cheshire/generate-string))]
    (client/post url (merge {:form-params param-map}
                            (when content-type {:content-type content-type})))))

(defn- parse-body [result]
  (-> result
      :body
      cheshire/parse-string
      walk/keywordize-keys))

(defn- transform-user-info [user-record]
  (let [{:keys [id team_id name real_name tz is_bot deleted]
         {:keys [first_name last_name email]}
         :profile} user-record]
    {:id id :team-id team_id :name name :real-name real_name
     :timezone tz :first-name first_name
     :last-name last_name :email email
     :bot? is_bot :deleted? deleted}))

(defn list-members
  "List the members of the team for the given access token."
  [access-token]
  (let [user-list-result
        (get-url "https://slack.com/api/users.list" :token access-token)

        {:keys [ok members] :as body}
        (parse-body user-list-result)]
    (if ok
      (->> members
           (filter #(not (and (:is_bot %) (:deleted %))))
           (filter #(not= "slackbot" (:name %)))
           (map transform-user-info))
      (ss/throw+ {:type ::user-list-error :body body}))))

(defn get-user-info "Gets information about a user."
  [access-token user-id]
  (let [user-info-result
        (get-url "https://slack.com/api/users.info"
                 :token access-token :user user-id)

        {:keys [ok user] :as body}
        (parse-body user-info-result)]
    (if ok
      (transform-user-info user)
      (log/errorf "Unable to get user info: %s" body))))

(defn get-channel-name "Gets information about a channel."
  [access-token channel-id]
  (let [channel-info-result
        (get-url "https://slack.com/api/channels.info"
                 :token access-token :channel channel-id)

        {:keys [ok channel] :as body}
        (parse-body channel-info-result)]
    (if ok
      (:name channel)
      (log/errorf "Unable to get channel info: %s" body))))

(defn buttons-to-attachment [{:keys [callback-id buttons]}]
  (when buttons {:text "1=Completely Disagree, 5=Completely Agree"
                 :callback_id callback-id
                 :actions (map #(let [{:keys [name value]} %]
                                  {:name name
                                   :text value
                                   :type "button"
                                   :value value}) buttons)}))

(defn image-to-attachment [{:keys [url description]
                            :or {description "Image"}}]
  {:image_url url :fallback description :text description})

(def ^:private attachment-converters
  {:buttons buttons-to-attachment :image image-to-attachment})

(defn convert-attachment [{:keys [type] :as attachment}]
  ((attachment-converters type) attachment))

(defn send-message!
  "Send a message to a channel."
  ([access-token channel message attachments]
   (let [processed-attachments (map convert-attachment attachments)
         _ (log/infof "Sending '%s' with attachments %s to '%s'"
                      message attachments channel)
         result (post-url! "https://slack.com/api/chat.postMessage"
                           :token access-token
                           :channel channel
                           :text message
                           :attachments processed-attachments
                           :as_user true)]
     (log/debugf "Result of message dispatch: %s" result)))
  ([access-token channel message]
   (send-message! access-token channel message nil)))

(defn send-response! [response-url message]
  (post-url! response-url
             :content-type :json
             :text message
             :replace_original false
             :response_type "ephemeral"))

(defn get-slack-auth [code]
  (let [auth-result
        (get-url "https://slack.com/api/oauth.access" :code code)]
    (parse-body auth-result)))

(defn auth-slack [code on-success]
  (let [{:keys [ok access_token user_id team_name team_id]
         {:keys [bot_user_id bot_access_token]} :bot
         :as body} (get-slack-auth code)]
    (if ok
      (do
        (log/infof "Authorization successful. Body: %s" body)
        (on-success :access-token access_token
                    :bot-access-token bot_access_token
                    :user-id user_id
                    :team-id team_id
                    :team-name team_name
                    :bot-user-id bot_user_id))
      (log/errorf "Authorization failed. Body: %s" body))
    ok))

(defn is-im-to-me?
  "Returns the given channel if it IS an IM to the given bot access token,
   otherwise nil"
  [bot-access-token channel]
  (let [ims (->> (get-url "https://slack.com/api/im.list"
                          :token bot-access-token)
                 parse-body :ims (map :id) (into #{}))]
    (ims channel)))

(defn challenge-response [{:keys [challenge]}]
  (when challenge {:challenge challenge}))