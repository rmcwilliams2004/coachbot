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

(ns coachbot.db-spec
  (:require [coachbot.db :as db]
            [clojure.java.jdbc :as jdbc]
            [speclj.core :refer :all]
            [taoensso.timbre :as log])
  (:import (java.io File)))

; Change :error to :info to see the database schema
(log/set-level! :error)

(describe "Database schema"
  (with-all ds (db/make-db-datasource "h2" "jdbc:h2:mem:test" "" ""))
  (after-all (jdbc/execute! @ds ["drop all objects"]))
  (it "should dump the full schema"
    (jdbc/query @ds ["script simple nodata to 'schema.sql'"])
    (let [f (File. "schema.sql")
          _ (log/info (slurp f))
          exists? (.exists f)
          deleted? (.delete f)]
      (should exists?)
      (should deleted?))))