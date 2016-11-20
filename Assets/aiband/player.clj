;; Copyright 2016 Douglas P. Fields, Jr.
;; symbolics _at_ lisp.engineer
;; https://symbolics.lisp.engineer/
;; https://twitter.com/LispEngineer
;; https://github.com/LispEngineer
;;;; Aiband - The Artificial Intelligence Roguelike

;;;; Aiband player-related functionality

;; TODO: Make the player object an entity?

(ns aiband.player
  (:require [aiband.level :as lv :reload true]))


(defn create-player
  "Creates an empty player object and places them in the level."
  ;; Place the player in the level on a random floor tile
  [level]
  (let [[start-x start-y] (lv/rand-location-t level :floor)]
    ;; TODO: Change this from :x :y to :coord [x y]
    {:x start-x :y start-y :hp 10 :hp-max 10}))


