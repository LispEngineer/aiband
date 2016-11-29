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
            [clojure.set :as set]
            [clojure.algo.monads :as µ
             ;; We specifically don't want update-val and update-state as we
             ;; made better versions of these
             :refer [domonad with-state-field fetch-val set-val]]
            [aiband.monads :as aµ :refer :all :reload true]))


;; Game Commands -----------------------------------------------------------------

(defn ɣ•open-door
  "State game-state: Open the door at the specified location, which should be
   adjacent to the player. (TODO: Check this?)"
  [x y]
  (dostate
    [terr (fetch-in-val [:level :terrain y x])
     :if (= terr :door-closed)
     :then [_ (lv/ɣ•set-terrain [x y] :door-open)
            _ (msg/ɣ•add "Door opened")
            retval (<- true)]
     ;; TODO: Convert to cond and check for already open door
     :else [_ (msg/ɣ•add "Cannot open")
            retval (<- false)]]
    retval)) 


(defn ɣ•move
  "State game-state monad: Moves the player object in this game by the specified delta.
   If not possible, adds a message to that effect and doesn't update the player.
   Returns true if the player actually moved. The state always changes, as if the
   player doesn't successfully move, a message is added to the message log."
  [dx dy]
  ;; TODO: Check that dx/dy are at most magnitude 1
  (dostate
    [p-x           (fetch-in-val [:player :x])
     p-y           (fetch-in-val [:player :y])
     w             (fetch-in-val [:level :width])
     h             (fetch-in-val [:level :height])
     t             (fetch-in-val [:level :terrain])
     level         (fetch-val    :level)
     :let [nx      (+ p-x dx)
           ny      (+ p-y dy)]
     ;; Let's try the <- function
     terrain       (<- (get2d t nx ny))
     :cond [
       ;; Moving out of range of the level (should never happen)
       (or (< nx 0) (< ny 0) (>= nx w) (>= ny h))
       [_      (msg/ɣ•add (str "Invalid move: " nx "," ny))
        retval (<- false)]
       ;; Walking into a closed door opens it (and put that in log) but doesn't move
       (= terrain :door-closed)
       [retval (ɣ•open-door nx ny)]
       ;; Moving into invalid terrain
       ;; TODO: Make a "terrain-walkable?" function
       (not (some #(= % terrain) [:floor :door-open]))
       [_      (msg/ɣ•add (str "Invalid terrain: " nx "," ny ": " terrain))
        retval (<- false)]
       ;; Otherwise, normal move
       :else
       [_      (set-in-val [:player :x] nx)
        _      (set-in-val [:player :y] ny)
        _      (» :level lv/µ•update-visibility [nx ny] see-dist)
        retval (<- true)]]]
    retval))    

;; Test the above
#_((aiband.commands/ɣ•move -3 -3) aiband.game/test-game-state) 
