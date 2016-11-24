;; Copyright 2016 Douglas P. Fields, Jr.
;; symbolics _at_ lisp.engineer
;; https://symbolics.lisp.engineer/
;; https://twitter.com/LispEngineer
;; https://github.com/LispEngineer
;;;; Aiband - The Artificial Intelligence Roguelike

;;;; Aiband player-related functionality

;; TODO: Make the player object an entity?

(ns aiband.player
  (:require [aiband.level :as lv :reload true]
            [aiband.random :as rnd :reload true]
            [clojure.algo.monads :as µ
             ;; We specifically don't want update-val and update-state as we
             ;; made better versions of these
             :refer [domonad with-state-field fetch-val set-val]]
            [aiband.monads :as aµ :refer :all :reload true]))


(defn create-player
  "Creates an empty player object and places them in the level."
  ;; Place the player in the level on a random floor tile
  [level]
  (let [[start-x start-y] (lv/rand-location-t level :floor)]
    ;; TODO: Change this from :x :y to :coord [x y]
    {:x start-x :y start-y :hp 10 :hp-max 10}))

(defn ɣ•create
  "State game-state: Creates an empty player and places them
   in a valid, random location in the level, replacing
   whatever else was in this game state for a player. Use only
   when creating a new game state. Returns true."
  []
  (dostate
    [[start-x start-y] (rnd/ɣ•rand-location-t :floor) ; FIXME: Will this crash if it returns nil? I think so.
     old-player        (set-val :player 
                          {:x start-x :y start-y
                           :hp 12 :hp-max 12})]
    true)) 

;; Test the above
#_(do
  ((ɣ•create) aiband.game/test-game-state)
  ((dostate [_ (ɣ•create) _ (ɣ•create)] true) aiband.game/test-game-state))
