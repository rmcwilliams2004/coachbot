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

(ns coachbot.slugid-spec
  (require [coachbot.slugid :as s]
           [speclj.core :refer :all]))
(describe "Slug generation"
          (with items (take 5000 (repeatedly s/random-slug)))
          (with lengths (map count @items))

          (it "should generate unique items"
              (should (apply distinct? @items)))

          (it "should generate small items"
              (should (>= 22 (apply max @lengths))))

          (it "should make sure they are URL-friendly"
              (should (every? #(re-matches #"[a-zA-Z0-9\-_]*" %) @items))))