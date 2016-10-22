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

(defproject coachbot "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [metosin/compojure-api "1.1.8"]]
  :ring {:handler coachbot.handler/app}
  :uberjar-name "server.jar"
  :test-paths ["spec"]
  :profiles {:dev {:dependencies [[javax.servlet/javax.servlet-api "3.1.0"]
                                  [cheshire "5.6.3"]
                                  [ring/ring-mock "0.3.0"]
                                  [speclj "3.3.2"]]
                   :plugins [[lein-ancient "0.6.10"]
                             [lein-bikeshed "0.3.0"]
                             [lein-kibit "0.1.2"]
                             [ikitommi/lein-ring "0.9.8-FIX"]
                             [speclj "3.3.2"]]}})