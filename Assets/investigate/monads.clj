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
  (:require [clojure.set :as set]
            [clojure.algo.monads :as m]))

;; Common -------------------------------------------------------------------

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
   :messages {:initial 0 :final 0 :text []}})

(defonce game-state
  (atom test-game-state))

;; Test 1: State monad with a game-state object threaded through
;; several methods which each update one or several parts of the
;; game state and each of which can return a value as well as an
;; updated state.

;; Utility function "in" version from monads.
;; TODO: Add a & rest so you can have a function with other args
(defn update-in-val
  "Return a state-monad function that assumes the state to be a map and
   replaces the value associated with the given keys vector by the return value
   of f applied to the old value. The old value is returned."
  [keys f]
  (fn [s]
    (let [old-val (get-in s keys)
          new-s   (assoc-in s keys (f old-val))]
      [old-val new-s])))

;; Utility function "in" version from monads.
(defn fetch-in-val
  "Return a state-monad function that assumes the state to be a nested map and
   returns the value corresponding to the given keys vector. The state is not modified."
  [keys]
  (fn [s] [(get-in s keys) s]))

(defn add-message-m-v1
  "Monad: Adds a message to the game state. Returns number of
   messages in the game state now."
  [message]
  (fn [s]
    (let [new-s (-> s
            (update-in ,,, [:messages :text] conj ,,, message)
            (update-in ,,, [:messages :final] inc))
          retval (- (:final (:messages new-s))
                    (:initial (:messages new-s)))]
      [retval new-s])))

;; Use the above
#_(do
  ;; This returns a function that needs a state input
  (m/domonad m/state-m [retval (add-message-m-v1 "hi")] retval) 
  ;; Use the above function on the test state; the return is the
  ;; [retval final-state]
  ((m/domonad m/state-m [_ (add-message-m-v1 "hi") retval (add-message-m-v1 "second hi")] retval) test-game-state))

(defn add-message-gs-m
  "Monad: Adds a message to the game state. Returns number of
   messages in the game state now."
  [message]
  (m/domonad m/state-m
    [_       (update-in-val [:messages :text] #(conj % message))
     _       (update-in-val [:messages :final] inc)
     final   (fetch-in-val  [:messages :final])
     initial (fetch-in-val  [:messages :initial])]
    (- final initial)))

;; Use the above
#_(do
  ;; This returns a function of the state
  (add-message-gs-m "Hello")
  ;; This returns the return value and the new state as a vector
  ((add-message-gs-m "Hello") test-game-state)
  ;; Composes this twice...
  ((m/domonad m/state-m [_ (add-message-gs-m "Hello") rv (add-message-gs-m "Second")] rv) test-game-state))

(defn add-message-m
  "State messages: Adds a message to the messages map of game-state. 
   Can be called on the game-state itself using with-state-field."
  [message]
  (m/domonad m/state-m
    [_       (m/update-val :text #(conj % message))
     _       (m/update-val :final inc)
     final   (m/fetch-val  :final)
     initial (m/fetch-val  :initial)]
    (- final initial)))

;; Use the above
#_(do
  ((add-message-m "First") (:messages test-game-state))
  ((m/domonad m/state-m [_ (add-message-m "First") rv (add-message-m "Second")] rv) (:messages test-game-state))
  ;; Use this with the larger game-state
  ;; with-state-field is a lot like zoom in Haskell lenses
  ((m/with-state-field :messages (add-message-m "First")) test-game-state))

(defn move-player-m
  "State player: Moves the player the specified dx/dy distance.
   Can be called on the game-state itself using with-state-field."
  [dx dy]
  (m/domonad m/state-m
    [_       (m/update-val :x (partial + dx))
     _       (m/update-val :y (partial + dy))]
     ;; TODO: Return an error message if we couldn't move, or nil if no error
     nil))

;; Use the above
#_((move-player-m 1 1) (:player test-game-state))


;; Test multiple calls to sub-segments of the game state
(defn gs-move-player-m
  "State game: Moves the player the specified delta and adds a message
   that the player was moved."
  [dx dy]
  (m/domonad m/state-m
    [_ (m/with-state-field :player   (move-player-m dx dy))
     _ (m/with-state-field :messages (add-message-m (str "Moved by " dx "," dy)))]
    ;; No return value
    nil))

;; Use the above
#_((gs-move-player-m 1 1) test-game-state)

(defmacro dostate
  "Does a state monad. The same as (domonad state-m ...). Syntactic sugar."
  [& args]
  (apply list 'm/domonad 'm/state-m args))

(defmacro zoom
  "Renames m/with-state-field to zoom"
  [& args]
  (apply list 'm/with-state-field args))

;; TODO: Write a macro dostate' which is a simplified verison of dostate
;; which takes just monadic forms and returns the return value of the last
;; monadic form. These forms could be in the form:
;;   (form) or function
;;   binding <- (form) or function (In a future version)
;; Also, see m-chain.

(defn gs-move-player-m-v2
  "State game: Moves the player the specified delta and adds a message
   that the player was moved."
  [dx dy]
  (dostate
    [_ (zoom :player   (move-player-m dx dy))
     _ (zoom :messages (add-message-m (str "Moved by " dx "," dy)))]
    ;; No return value
    nil))

;; Use the above
#_((gs-move-player-m-v2 1 1) test-game-state)

(defn gs-test-let-m
  "State game: Test the :let, :cond and :if items in domonad."
  [dx dy]
  (dostate
    [_ (zoom :player   (move-player-m dx dy))
     x (zoom :player (m/fetch-val :x))
     y (fetch-in-val [:player :y])
     :let [xplusy (+ x y)
           xplus1 (inc x)]
     _ (zoom :messages (add-message-m (str "Moved by " dx "," dy)))
     ;; Examples: https://github.com/clojure/algo.monads/blob/master/src/test/clojure/clojure/algo/test_monads.clj
     :if (> x 3)
     :then [_ (zoom :messages (add-message-m (str "Ha! You moved too far right! " x)))]
     :else [_ (zoom :messages (add-message-m "I dare you to move further right."))]
     :cond [
       (< y 3)
       [_  (zoom :messages (add-message-m "You're really low."))]
       (< y 6)
       [_  (zoom :messages (add-message-m "You're not too low."))]
       :else
       [_  (zoom :messages (add-message-m "You're high!!!"))]]
     _ (zoom :messages (add-message-m (str "Thank you for playing: " xplusy)))]
    ;; Use our let in the return values
    (* xplusy xplus1)))

;; Use the above in all possible cases
#_(do
  ((gs-test-let-m 1 1) test-game-state)
  ((gs-test-let-m 4 4) test-game-state)
  ((gs-test-let-m 4 2) test-game-state)
  ((gs-test-let-m 4 -2) test-game-state))


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
  "->: Adds this message to the game state."
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

