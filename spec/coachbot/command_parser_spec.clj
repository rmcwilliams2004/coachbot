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

(ns coachbot.command-parser-spec
  (:require [coachbot.command-parser :refer :all]
            [speclj.core :refer :all]))

(describe "Command Parsing"
  (context "Simple commands"
    (it "should parse a simple command"
      (should= ["help"] (parse-command "help"))
      (should= ["help"] (parse-command "Help"))
      (should= ["help"] (parse-command "Help "))
      (should= ["help"] (parse-command " Help")))

    (it "should get a single argument"
      (should= ["tell coach" "i have a question"]
               (parse-command "tell coach i have a question"))))

  (context "Bad Commands"
    (it "should throw nice exceptions for bad commands"
      (should-throw Exception
        #":type :coachbot.command-parser/parse-failure"
        (parse-command "die scum"))))

  (context "Funky Commands"
    (context "start coaching"
      (it "start coaching at 9am"
        (should= ["start coaching" "9am"]
                 (parse-command "start coaching at 9am")))
      (it "start coaching at 9 am"
        (should= ["start coaching" "9 am"]
                 (parse-command "start coaching at 9 am")))
      (it "start coaching at 9 PM"
        (should= ["start coaching" "9 PM"]
                 (parse-command "start coaching at 9 PM")))
      (it "start coaching at 10pM"
        (should= ["start coaching" "10pM"]
                 (parse-command "start coaching at 10pM")))
      (it "start coaching at 10 a.m."
        (should= ["start coaching" "10 a.m."]
                 (parse-command "start coaching at 10 a.m.")))
      (it "start coaching at 10 a.m."
        (should= ["start coaching" "10 P.M."]
                 (parse-command "start coaching at 10 P.M.")))
      (it "start coaching at 10 PM."
        (should= ["start coaching" "10 PM."]
                 (parse-command "start coaching at 10 PM.")))
      (it "start coaching at 10 PM."
        (should= ["start coaching" "10 PM."]
                 (parse-command "start coaching at 10 PM.")))
      (it "start coaching at 10 P.M"
        (should= ["start coaching" "10 P.M"]
                 (parse-command "start coaching at 10 P.M")))
      (it "start coaching at 13pm"
        (should-throw (parse-command "start coaching at 13pm")))
      (it "start coaching at 91am"
        (should-throw (parse-command "start coaching at 91am"))))))
