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

(ns ^{:doc "Generate base64-encoded URL-friendly UUIDs. Inspired by
            https://www.npmjs.com/package/slugid"}
coachbot.slugid
  (:require [clojure.data.codec.base64 :as b64]
            [clojure.string :as str])
  (:import (java.nio ByteBuffer)
           (java.util UUID)))

(defn random-uuid
  "Generate a random UUID as a simple byte array."
  []
  (let [byte-array (byte-array 16)
        byte-buffer (ByteBuffer/wrap byte-array)
        uuid (UUID/randomUUID)]
    (doto byte-buffer (.putLong (.getMostSignificantBits uuid))
                      (.putLong (.getLeastSignificantBits uuid)))
    (.array byte-buffer)))

(defn random-slug
  "Generate a random UUID encoded using base64 suitable for putting on a
   URL."
  []
  (-> (random-uuid)
      (b64/encode)
      (String.)
      (subs 0 22)
      (str/replace #"\+" "-")
      (str/replace #"\/" "_")))