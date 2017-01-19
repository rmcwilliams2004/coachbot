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

(ns coachbot.hsql-utils
  (:require [clojure.java.jdbc :as jdbc]
            [honeysql.core :as sql]
            [taoensso.timbre :as log]))

(defn execute-safely! [hq conn]
  (let [stmt (sql/format hq)]
    (try (jdbc/execute! conn stmt)
         (catch Throwable t
           (log/errorf t "Unable to execute: %s" stmt)))))

(defn query
  ([hq conn]
   (->> hq
        sql/format
        (jdbc/query conn)))
  ([hq conn f]
   (map f (query hq conn))))