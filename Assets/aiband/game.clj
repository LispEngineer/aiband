;; Copyright 2016 Douglas P. Fields, Jr.
;; symbolics _at_ lisp.engineer
;; https://symbolics.lisp.engineer/
;; https://twitter.com/LispEngineer
;; https://github.com/LispEngineer
;;;; Aiband - The Artificial Intelligence Roguelike

;;;; Overall game-state related functionality, as well as functions that cross
;;;; boundaries across multiple portions of the game-state

(ns aiband.game
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

;; Items & Level ------------------------------------------------------------
;; Functions that have a dependency on both level and item data structures
;; so we can't include them in either of those packages without creating
;; a circular dependency.

;; TODO: Have a random item generator that can then be stored
;; in the level
(defn create-random-item-in
  "Creates a random item that is on a floor space of the
   specified level. Returns as [[x y] entity]. NOT PURE."
  [lv]
  (let [[x y] (lv/rand-location-t lv :floor)
        i-type (get [:ring :amulet] (rand-int 2))]
    [[x y] (i/create-item i-type)]))

(defn create-items-in
  "Creates some random items in this level on floor spaces.
   Returns the level with the entities added.
   NOT PURE."
  [lv]
  (reduce
    (fn [lv [coord entity]]
      (assoc lv :entities (i/add-entity (:entities lv) coord entity)))
    lv
    (take (+ lv/min-items (rand-int (- lv/max-items lv/min-items -1)))
      (repeatedly (fn [] (create-random-item-in lv))))))

(defn create-level
  "Creates a new random level with items in it."
  []
  (create-items-in (lv/create-empty-level)))


;; Messages ---------------------------------------------------------------------

(defn add-message
  "Adds a message to a game object, returning a new game object
   that is unchanged except for the messages."
  [game new-msg]
  (assoc game :messages
    (msg/add (:messages game) new-msg)))

;; Game infrastructure --------------------------------------------------------

(defn update-game!
  "Updates the global *game* by calling the specified function
   with the current game state as the first arg with the rest of
   the args. The return value should be [new-state error]. If new-state
   is non-nil, then we save it to *game*.
   TODO: If the new-state is nil, we add the string error to the
   messages of the (previous) game state, and update *game*."
  [func & rest]
  (println func)
  (println rest)
  (let [[new-game error :as retval]
        (apply func @game-state rest)]
    (if new-game 
      (reset! game-state new-game)
      (reset! game-state (add-message @game-state error)))
    [@game-state error]))


(defn create
  "Creates a new game object with a player and a random level with random items."
  []
  (let [level (create-level)
        player (p/create-player level)
        ;; Update what the player can see from the start
        level-vis (lv/update-level-visibility level [(:x player) (:y player)] see-dist)]
    {:level level-vis
     :player player
     :messages (msg/create)}))

;; Game State and Initialization ---------------------------------------------

(defn initialize-game
  "Initializes a game with new, random state. We don't do this in the
   'game-state' def because having it be called explicitly allows us to
   reload this file in REPL without modifying the game state."
  []
  (reset! game-state (create)))


