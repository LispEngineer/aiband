;; Copyright 2016 Douglas P. Fields, Jr.
;; symbolics _at_ lisp.engineer
;; https://symbolics.lisp.engineer/
;; https://twitter.com/LispEngineer
;; https://github.com/LispEngineer
;;;; Aiband - The Artificial Intelligence Roguelike

;;;; Global variales. Currently only game state and some configuration.

(ns aiband.globals)


;; Configuration --------------------------------------------------------------

(def see-dist
  "How far the player can see in a straight line, using
   Chebyshev distance."
  4)


;; Atoms ----------------------------------------------------------------------

;; The current full state of the Aiband game right now EXCEPT for
;; the random number generator seed.
;; TODO: Add a doc-string to game-state
(defonce game-state (atom nil))
