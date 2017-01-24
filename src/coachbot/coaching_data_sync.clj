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

(ns coachbot.coaching-data-sync
  (:require [clojure-csv.core :as csv]
            [coachbot.hsql-utils :as hu]
            [clojure.java.io :as io]
            [clojure.java.jdbc :as jdbc]
            [honeysql.helpers :as h]
            [semantic-csv.core :as sc]
            [taoensso.timbre :as log]))

(defn- load-expected-questions []
  (with-open [in-file (io/reader (io/resource "question_groups.csv"))]
    (->> (csv/parse-csv in-file)
         (sc/mappify {:keyify false})
         (map #(->> % (remove (fn [[_ v]] (= "" v))) (into {})))
         (map #(let [{:strs [Question] :as row} %
                     categories (-> row
                                    (dissoc "Question")
                                    keys)]
                 {:question Question
                  :categories categories}))
         doall)))

(defn- load-actual-questions [ds]
  (->> (jdbc/query ds ["select id, question from base_questions"])
       (group-by :question)
       (map #(let [[question options] %] {question (map :id options)}))
       (apply merge)))

(defn- load-groups [ds]
  (->> (jdbc/query ds ["select id, group_name from question_groups"])
       (map #(let [{:keys [id group_name]} %] {group_name id}))
       (apply merge)))

(defn- update-question-categories [conn ids categories]
  (do
    (-> (h/delete-from :bq_question_groups)
        (h/where [:in :question_id ids])
        (hu/execute-safely! conn))

    (-> (h/insert-into :bq_question_groups)
        (h/columns :question_id :question_group_id)
        (h/values (->> ids
                       (map #(interleave (repeat (count categories) %)
                                         categories))
                       flatten
                       (partition-all 2)))
        (hu/execute-safely! conn))))

(defn update-categories-for-questions
  "Ensures that every question in the database is correctly associated with
   all the categories it is supposed to be associated with."
  [ds]
  (let [expected-questions (load-expected-questions)
        actual-questions (load-actual-questions ds)
        groups (load-groups ds)
        merged-questions (map #(let [{:keys [question categories]} %
                                     ids (get actual-questions question)]
                                 {:question question
                                  :ids ids

                                  :categories
                                  (map groups categories)})
                              expected-questions)]
    (jdbc/with-db-transaction
      [conn ds]
      (doseq [{:keys [ids question categories]} merged-questions]
        (if ids (update-question-categories conn ids categories)
                (log/errorf "Question not found to add categories to: %s"
                            question))))

    ds))