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

(ns coachbot.command-parser
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [instaparse.core :as insta])
  (:use [slingshot.slingshot :only [throw+ try+]]))

(def ^:private parse-using-ebnf
  (insta/parser (str (io/resource "commands.ebnf")) :string-ci true))

(defn parse-command [command]
  (let [result (parse-using-ebnf (str/trim command))]
    (if (insta/failure? result)
      (throw+ {:type ::parse-failure :result (pr-str result)})
      (first result))))