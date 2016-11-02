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

(ns coachbot.command-parser
  (:require [clojure.java.io :as io]
            [instaparse.core :as insta]))

(def ^:private parse-governance
  (insta/parser (io/resource "commands.ebnf") :string-ci true))

(defn parse-command [command]
  (let [parsed-document (parse-governance command)]
    (if (insta/failure? parsed-document)
      (throw (IllegalArgumentException. (pr-str parsed-document)))
      parsed-document)))