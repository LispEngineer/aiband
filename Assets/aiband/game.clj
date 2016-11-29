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
            [aiband.clrjvm :refer :all :reload true]
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
             :refer [domonad with-state-field fetch-val]]
            [aiband.monads :as aµ :refer :all :reload true]))

;; Minimal game state for testing purposes ----------------------------------

(def test-game-state
  "A populated but not very complex test game state."
  {:level
    {:width 5 :height 5
     :terrain [ [ :rock :wall  :wall  :wall  :wall ]
                [ :wall :floor :floor :floor :wall ]
                [ :wall :floor :floor :wall  :wall ]
                [ :wall :wall  :floor :wall  :rock ]
                [ :rock :wall  :wall  :wall  :wall ] ]
     :seen #{} :visible #{}
     :entities {}}
   :player {:x 2 :y 2 :hp 10 :hp-max 10}
   ;; TODO: Replace messages with a writer monad? 
   :messages {:initial 0 :final 0 :text []}
   :rng 1})

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

;; Game infrastructure --------------------------------------------------------

(defn update-ɣ!
  "Update the current game state atom by calling the specified state monad function
   on the current game state. Updates the game state with the new state,
   and returns the return value. If the new state is invalid (e.g., nil), then
   we don't update the state."
  [ɣ•func & rest]
  (dosync
    (let [[retval final-state] ((apply ɣ•func rest) @game-state)]
      ;; TODO: Check that the state is valid before updating it
      (when final-state (reset! game-state final-state))
      retval)))

;; Test the above
#_(do
  (reset! aiband.globals/game-state aiband.game/test-game-state)
  (aiband.game/update-ɣ! aiband.commands/ɣ•move -1 -1)
  @aiband.globals/game-state)


;; TODO: Create game in a fully monadic way from an empty shell with a
;; random seed.
(defn create
  "Creates a new game object with a player and a random level with random items."
  []
  ;; TODO: Create the random seed (or take it as a parameter) and then use that
  ;; to call all the other create functions.
  (let [level (create-level)
        player (p/create-player level)
        ;; Update what the player can see from the start
        level-vis (lv/update-level-visibility level [(:x player) (:y player)] see-dist)
        initial-gs 
          {:level level-vis
           :player player
           :messages (msg/create)
           :rng (rnd/make-seed (time-ms))}]
    ;; Now, switch to monadic language and add doors.
    (second ((lv/ɣ•add-doors) initial-gs))))

;; Game State and Initialization ---------------------------------------------

(defn initialize-game
  "Initializes a game with new, random state. We don't do this in the
   'game-state' def because having it be called explicitly allows us to
   reload this file in REPL without modifying the game state."
  []
  (reset! game-state (create)))
