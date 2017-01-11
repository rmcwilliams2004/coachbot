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
            [speclj.core :refer :all]
            [taoensso.timbre :as log]))

(describe "Command Parsing"
  (around-all [it] (log/with-level :error (it)))

  (context "Simple commands"
    (it "should parse a simple command"
      (should= [:help] (parse-command "help"))
      (should= [:help] (parse-command "Help"))
      (should= [:help] (parse-command "Help "))
      (should= [:help] (parse-command " Help")))

    (it "should get a single argument"
      (should= [:tell-coach "i have a question"]
               (parse-command "tell coach i have a question")))

    (context "question groups"
      (it "should allow lots of variants"
        (should= [:show-groups] (parse-command "group"))
        (should= [:show-groups] (parse-command "groups"))
        (should= [:show-groups] (parse-command "show group"))
        (should= [:show-groups] (parse-command "show groups"))
        (should= [:show-groups] (parse-command "question group"))
        (should= [:show-groups] (parse-command "question groups"))
        (should= [:show-groups] (parse-command "show question group"))
        (should= [:show-groups] (parse-command "show question groups"))))

    (context "add question groups"
      (it "should allow lots of variants"
        (should= [:add-group "bill"] (parse-command "add group bill"))
        (should= [:add-group "bill"] (parse-command "add to group bill"))
        (should= [:add-group "bill"] (parse-command "add groups bill"))
        (should= [:add-group "bill"] (parse-command "add to groups bill"))
        (should= [:add-group "bill"]
                 (parse-command "add to question groups bill"))
        (should= [:add-group "bill"] (parse-command "add question group bill"))
        (should= [:add-group "bill"]
                 (parse-command "add question groups bill"))))

    (context "remove question groups"
      (it "should allow lots of variants"
        (should= [:remove-group "bill"] (parse-command "remove group bill"))
        (should= [:remove-group "bill"]
                 (parse-command "remove from group bill"))
        (should= [:remove-group "bill"] (parse-command "remove groups bill"))
        (should= [:remove-group "bill"]
                 (parse-command "remove from groups bill"))
        (should= [:remove-group "bill"]
                 (parse-command "remove from question groups bill"))
        (should= [:remove-group "bill"]
                 (parse-command "remove question group bill"))
        (should= [:remove-group "bill"]
                 (parse-command "remove question groups bill")))))

  (context "Bad Commands"
    (it "should throw nice exceptions for bad commands"
      (should-throw Exception
        #":type :coachbot.command-parser/parse-failure"
        (parse-command "die scum"))))

  (context "Funky Commands"
    (context "start coaching"
      (it "start coaching at 9am"
        (should= [:start-coaching "9am"]
                 (parse-command "start coaching at 9am")))
      (it "start coaching at 9 am"
        (should= [:start-coaching "9 am"]
                 (parse-command "start coaching at 9 am")))
      (it "start coaching at 9 PM"
        (should= [:start-coaching "9 PM"]
                 (parse-command "start coaching at 9 PM")))
      (it "start coaching at 10pM"
        (should= [:start-coaching "10pM"]
                 (parse-command "start coaching at 10pM")))
      (it "start coaching at 10 a.m."
        (should= [:start-coaching "10 a.m."]
                 (parse-command "start coaching at 10 a.m.")))
      (it "start coaching at 10 a.m."
        (should= [:start-coaching "10 P.M."]
                 (parse-command "start coaching at 10 P.M.")))
      (it "start coaching at 10 PM."
        (should= [:start-coaching "10 PM."]
                 (parse-command "start coaching at 10 PM.")))
      (it "start coaching at 10 PM."
        (should= [:start-coaching "10 PM."]
                 (parse-command "start coaching at 10 PM.")))
      (it "start coaching at 10 P.M"
        (should= [:start-coaching "10 P.M"]
                 (parse-command "start coaching at 10 P.M")))
      (it "start coaching at 13pm"
        (should-throw (parse-command "start coaching at 13pm")))
      (it "start coaching at 91am"
        (should-throw (parse-command "start coaching at 91am")))))

  (context "Friendly Commands"
    (it "Should say :) to lots of friendly commands"
      (should= [:friendly] (parse-command "thanks"))
      (should= [:friendly] (parse-command "thanks coachbot"))
      (should= [:friendly] (parse-command "thank you coachbot"))
      (should= [:friendly] (parse-command "thank you"))
      (should= [:friendly] (parse-command "Cheers")))))
