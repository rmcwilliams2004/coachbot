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

(ns coachbot.command-parser-spec
  (:require [coachbot.command-parser :refer :all]
            [speclj.core :refer :all]
            [taoensso.timbre :as log]))

(def ^:private start-coaching-pattern "start coaching at %s")

(defmacro should-parse [expected command]
  `(should= ~expected (parse-command ~command)))

(defmacro single-arg-variants [name pattern bindings arg-variants
                               & assertion]
  (let [variants
        (map (fn [variant]
               `(let [~(first bindings) (format ~pattern ~variant)
                      ~(second bindings) ~variant]
                  (it ~(first bindings) ~@assertion)))
             arg-variants)]
    `(context ~name ~@variants)))

(defmacro single-arg-successes [name pattern expected-command & arg-variants]
  `(single-arg-variants ~name ~pattern [command# variant#] ~arg-variants
                        (should-parse [~expected-command variant#] command#)))

(defmacro single-arg-failures [name pattern & arg-variants]
  `(single-arg-variants ~name ~pattern [command# _#] ~arg-variants
                        (should-throw (parse-command command#))))

(defmacro it-parses-variants [name expected & variants]
  (let [expectations
        (map (fn [variant] `(should-parse ~expected ~variant)) variants)]
    `(it (str "should parse variants of '" ~name "'") ~@expectations)))

(describe "Command Parsing"
  (around-all [it] (log/with-level :error (it)))

  (it-parses-variants "help" [:help] "help" "Help" "Help " " Help ")

  (it-parses-variants "friendly" [:friendly]
    "thanks" "thanks coachbot" "thank you coachbot" "thank you" "Cheers")

  (it "should get a single argument"
    (should-parse [:tell-coach "a question"] "tell coach a question"))

  (it-parses-variants "show groups" [:show-groups]
    "group" "groups" "show group" "show groups" "question group"
    "question groups" "show question group" "show question groups")

  (it-parses-variants "start coaching" [:start-coaching]
    "start coaching" "start" "Start coaching" "START COACHING")

  (it-parses-variants "start coaching at" [:start-coaching "6am"]
    "start coaching at 6am"
    "start at 6am")

  (it-parses-variants "stop coaching" [:stop-coaching]
    "stop coaching" "stop" "Stop coaching" "STOP COACHING")

  (it-parses-variants "next question" [:next-question]
    "another question" "next question" "next" "another"
    "Another Question" "NEXT" "AnOthER")

  (it-parses-variants "add group" [:add-group "bill"]
    "add group bill"
    "add to group bill"
    "add groups bill"
    "add to groups bill"
    "add to question groups bill"
    "add question group bill"
    "add question groups bill"
    "ADD GROUP bill")

  (it-parses-variants "remove group" [:remove-group "bill"]
    "remove group bill"
    "remove from group bill"
    "remove groups bill"
    "remove from groups bill"
    "remove from question groups bill"
    "remove question group bill"
    "remove question groups bill"
    "ReMoVe Group bill")

  (it-parses-variants "remove group" [:remove-group "Bill"]
    "remove group Bill" "RemoVe group Bill")

  (context "show questions"
    (it-parses-variants "last 1" [:show-questions]
      "show me the last question"
      "show me last question"
      "show last question"
      "show last")

    (it-parses-variants "simple" [:show-questions "5"]
      "show me the last 5"
      "show me last 5"
      "show the last 5"
      "show last 5 question"
      "show last 5 questions")

    (it-parses-variants "weeks" [:show-questions "2" "week"]
      "show me last 2 week"
      "show me the last 2 week"
      "show the last 2 week"
      "show last 2 week"
      "show last 2 weeks"
      "show last 2 weeks'"
      "show last 2 weeks' question"
      "show last 2 weeks questions"
      "show last 2 weeks of questions"
      "show last 2 weeks' questions")

    (it-parses-variants "days" [:show-questions "10" "day"]
      "show me last 10 day"
      "show me ThE last 10 day"
      "show the last 10 day"
      "show LAST 10 day"
      "show last 10 days"
      "show last 10 days'"
      "show last 10 days' Question"
      "show last 10 days questions"
      "show last 10 days OF questions"
      "show last 10 days' questions"))

  (it "should throw nice exceptions for bad commands"
    (should-throw Exception
                  #":type :coachbot.command-parser/parse-failure"
                  (parse-command "die scum")))

  (context "start coaching"
    (single-arg-successes "good" start-coaching-pattern :start-coaching
                          "9am" "9 am" "9 PM" "10pM" "10 a.m." "10 P.M."
                          "10 PM." "10 PM" "10 P.M")

    (single-arg-failures "bad" start-coaching-pattern
                         "13pm" "91am" "47" "111 A.M." "whenever"))

  (context "make assertions"
    (it-parses-variants "simple assertion"
      [:assert "marketing" "Our marketing department is amazing"]
      "assert to #marketing that \"Our marketing department is amazing\""
      "ASSERT to #marketing that \"Our marketing department is amazing\""
      "Assert To #marketing That \"Our marketing department is amazing\""
      "assert to #marketing \"Our marketing department is amazing\""
      "ASSERT #marketing \"Our marketing department is amazing\""
      "Assert #marketing \"Our marketing department is amazing\"")

    (it-parses-variants "reversed scale lowercase"
      [:assert "marketing" "Our marketing department is awful" "reversed"]
      (str "assert to #marketing that \"Our marketing department is awful\""
           " with reversed scale")
      "assert #marketing \"Our marketing department is awful\" reversed")

    (it-parses-variants "reversed scale uppercase"
      [:assert "marketing" "Our marketing department is awful" "REVERSED"]
      (str "ASSERT TO #marketing that \"Our marketing department is awful\""
           " WITH REVERSED SCALE")
      "ASSERT #marketing \"Our marketing department is awful\" REVERSED")))