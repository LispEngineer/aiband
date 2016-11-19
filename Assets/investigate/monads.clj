;; Copyright 2016 Douglas P. Fields, Jr.
;; symbolics _at_ lisp.engineer
;; https://symbolics.lisp.engineer/
;; https://twitter.com/LispEngineer
;; https://github.com/LispEngineer
;;;; Aiband - The Artificial Intelligence Roguelike

;;;; An investigation of the clojure.algo.monads and how its State monad
;;;; apply to our desired use for Aiband pure state management.

;; https://github.com/LonoCloud/synthread
;; https://github.com/rplevy/swiss-arrows

(ns investigate.monads
  (:require [clojure.set :as set]))


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
   :messages {:initial 0 :final 0 :text []}})

(defonce game-state
  (atom test-game-state))

;; Test 1: State monad with a game-state object threaded through
;; several methods which each update one or several parts of the
;; game state.

;; Test 2: Use the threading macro ->
;; which seems to be very similar to how we would use a state
;; monad. However, we have no way to return an error in the thread
;; and then prevent it from getting further passed through, while
;; still maintaining the last game state before the error.
;; Although you can use some-> to stop threading if the return value
;; is ever nil.

;; We also can't do intermediate computations and keep those results with
;; bindings here.

;; We also can't get the current game state

;; We need to figure out how to lift things as well.

(defn add-message->
  "Adds this message to the game state."
  [gs message]
  (-> gs
    (update-in [:messages :text] conj message) ; (fn [txt] (conj txt message)))
    (update-in [:messages :final] inc)))

(defn update-vis->
  "Updates the visibility of the level assuming the player its
   standing at location [x y]"
  [gs [x y :as coord]]
  ;; For test purposes, just see the one coord and the one next to it
  (let [now-vis #{coord [(inc x) y]}]
    (-> gs
      (assoc-in [:level :visible] now-vis)
      (update-in [:level :seen] set/union now-vis)))) 


(defn move-player->
  [gs dx dy]
  (-> gs
    (update-in [:player :x] + dx)
    (update-in [:player :y] + dy)
    (add-message-> (str "Player moved by " dx "," dy))
    ;; We can't get the current game state to get the player info here...
    ;; Use synthread's "do" and "<>"? 
    ;; Use swiss arrows and "-<>"?
    ))

