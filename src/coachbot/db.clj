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

(ns coachbot.db
  (:require [clojure.java.jdbc :as jdbc])
  (:import (com.zaxxer.hikari HikariConfig HikariDataSource)
           (java.io ByteArrayInputStream)
           (org.flywaydb.core Flyway)))

(def migration-base "db/migration")
(defn migration-of [db-type] (format "%s/%s" migration-base db-type))

(defn- migrate [db-type data-source]
  (doto (Flyway.)
    (.setLocations
      (into-array String [(migration-of "common") (migration-of db-type)]))
    (.setDataSource data-source)
    (.migrate)) data-source)

(defn make-db-datasource
  "Create a new database connection pool."
  ([db-type db-url db-username db-password]
   (make-db-datasource db-type db-url db-username db-password 15000 2))

  ([db-type db-url db-username db-password conn-timeout max-conn]
   {:datasource
    (migrate db-type
             (HikariDataSource. (doto (HikariConfig.)
                                  (.setConnectionTimeout conn-timeout)
                                  (.setJdbcUrl db-url)
                                  (.setUsername db-username)
                                  (.setPassword db-password)
                                  (.setAutoCommit false)
                                  (.setMaximumPoolSize max-conn))))}))

(def array-of-bytes-type (Class/forName "[B"))

(defn choose-binary-stream
  "MySQL returns byte arrays. H2 returns JdbcBlob objects."
  [cell]
  (let [type (type cell)]
    (if (= array-of-bytes-type type)
      (ByteArrayInputStream. cell)
      (.getBinaryStream cell))))

(defn choose-character-stream
  "MySQL returns byte arrays. H2 returns JdbcBlob objects."
  [cell]
  (let [type (type cell)]
    (if (= String type)
      (ByteArrayInputStream. (.getBytes cell))
      (.getAsciiStream cell))))

(defn fetch-last-insert-id [conn]
  (first (jdbc/query conn ["SELECT LAST_INSERT_ID() AS ID"] {:row-fn :id})))

(def extract-binary-data (comp slurp choose-binary-stream))
(def extract-character-data (comp slurp choose-character-stream))