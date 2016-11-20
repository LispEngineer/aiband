;; Copyright 2016 Douglas P. Fields, Jr.
;; symbolics _at_ lisp.engineer
;; https://symbolics.lisp.engineer/
;; https://twitter.com/LispEngineer
;; https://github.com/LispEngineer
;;;; Aiband - The Artificial Intelligence Roguelike


(ns aiband.core
  (:require [aiband.v2d :refer :all :reload true]
            [aiband.bsp :as bsp :reload true]
            [aiband.item :as i :reload true]
            [aiband.fov :as fov :reload true]
            [aiband.level :as lv :reload true]
            [aiband.player :as p :reload true]
            [aiband.messages :as msg :reload true]
            [aiband.random :as rnd :reload true]
            [aiband.game :as game :reload true]
            [aiband.commands :as commands :reload true]
            [aiband.globals :refer :all :reload true]
            [clojure.set :as set]))

;; Load our module into the REPL
#_(require '[aiband.core :refer :all :reload true])
#_(in-ns 'aiband.core)


;; Description -------------------------------------------------------------

(defn describe-at
  "Returns a single-line string describing what the player knows about
   the location at the specified [x y] coordinates."
  [[x y :as coord]]
  (let [gs @game-state
        tile (get2d (:terrain (:level gs)) coord)
        loc-visible (contains? (:visible (:level gs)) coord)
        seen-!vis   (set/difference (:seen (:level gs)) (:visible (:level gs)))
        loc-seen    (contains? seen-!vis coord)
        player-coord [(:x (:player gs)) (:y (:player gs))]
        player-text (if (= coord player-coord) ", You" "")
        items (get (:entities (:level gs)) coord)
        item-names (map #(i/item-type->name (:item-type %)) items)
        item-str1 (apply str (interpose ", " item-names))
        item-str (if (zero? (count item-str1))
                     ""
                     (str ", " item-str1))]
    (cond
      ;; User hasn't seen this place
      (and (not loc-visible) (not loc-seen))
      "Unknown"
      ;; If for some reason he can see rock...
      (= tile :rock) 
      "Unknown"
      ;; Otherwise show him what's there (or was there, if we track that)
      :else 
      (str ;; coord " - " 
        (if loc-seen "Not visible, was: " "")
        (lv/tile->name tile) item-str player-text))))
