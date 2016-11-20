;; Copyright 2016 Douglas P. Fields, Jr.
;; symbolics _at_ lisp.engineer
;; https://symbolics.lisp.engineer/
;; https://twitter.com/LispEngineer
;; https://github.com/LispEngineer
;;;; Aiband - The Artificial Intelligence Roguelike

;;;; End-user game commands which will all impact the game state.

;; These functions are all likely to be state monadic on the game state.
;; (when I get around to that)

(ns aiband.commands
  (:require [aiband.v2d :refer :all :reload true]
            [aiband.bsp :as bsp :reload true]
            [aiband.item :as i :reload true]
            [aiband.fov :as fov :reload true]
            [aiband.level :as lv :reload true]
            [aiband.player :as p :reload true]
            [aiband.messages :as msg :reload true]
            [aiband.random :as rnd :reload true]
            [aiband.globals :refer :all :reload true]
            [clojure.set :as set]))


;; Game Commands -----------------------------------------------------------------

;; TODO: Make this into a monadic version
(defn move
  "Moves the player object in this game by the specified delta.
   Returns [new-game error] with new-game like *game* and error string.
   new-game will be nil, and error non-nil, on error."
  [game dx dy]
  ;; TODO: Destructure the game using destructuring-let
  ;; TODO: Check that dx/dy are at most magnitude 1
  (let [player (:player game)
        level  (:level game)
        p-x    (:x player)
        p-y    (:y player)
        w      (:width level)
        h      (:height level)
        t      (:terrain level)
        nx     (+ p-x dx)
        ny     (+ p-y dy)]
    (cond 
      ;; Figure out every way the move could/should fail
      (or (< nx 0) (< ny 0) (>= nx w) (>= ny h)) [nil (str "Invalid move: " nx "," ny)]
      (not= (get2d t nx ny) :floor) [nil (str "Invalid terrain: " nx "," ny)]
      ;; Otherwise, move the player.
      :else
      [(-> game
         (assoc-in ,,, [:player :x] nx)
         (assoc-in ,,, [:player :y] ny)
         ;; Update visibility
         (assoc ,,, :level (lv/update-level-visibility (:level game) [nx ny] see-dist)))
       ""])))
