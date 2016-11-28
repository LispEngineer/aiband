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

;; TODO: Try this other more Haskell-seeming Monad library:
;;   https://github.com/bwo/monads
;;   The one above has a number of dependencies which may make it difficult to use.
;; Another one:
;;   https://github.com/jduey/protocol-monads
;; Haskell-inspired:
;;   http://fluokitten.uncomplicate.org/

(ns investigate.monads
  (:require [clojure.set :as set]
            ;; Clojure's monad library augmented by my stuff
            [clojure.algo.monads :as m
             ;; We specifically don't want update-val and update-state as we
             ;; made better versions of these
             :refer [domonad 
                     maybe-t state-t maybe-m state-m 
                     with-state-field fetch-val
                     state-m-until]]
            [aiband.monads :as am :refer :all :reload true]))


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
   :messages {:initial 0 :final 0 :text []}
   :rng 1})

(defonce game-state
  (atom test-game-state))

;; Test 1: State monad with a game-state object threaded through
;; several methods which each update one or several parts of the
;; game state and each of which can return a value as well as an
;; updated state.

;; Using clojure.algo.monads: https://github.com/clojure/algo.monads

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
  (domonad state-m [retval (add-message-m-v1 "hi")] retval) 
  ;; Use the above function on the test state; the return is the
  ;; [retval final-state]
  ((dostate [_ (add-message-m-v1 "hi") retval (add-message-m-v1 "second hi")] retval) test-game-state))

(defn add-message-gs-m
  "Monad: Adds a message to the game state. Returns number of
   messages in the game state now."
  [message]
  (domonad state-m
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
  ((domonad state-m [_ (add-message-gs-m "Hello") rv (add-message-gs-m "Second")] rv) test-game-state))

(defn add-message-m
  "State messages: Adds a message to the messages map of game-state. 
   Can be called on the game-state itself using with-state-field."
  [message]
  (domonad state-m
    [_       (update-val :text #(conj % message))
     _       (update-val :final inc)
     final   (fetch-val  :final)
     initial (fetch-val  :initial)]
    ;; Return current number of messages
    (- final initial)))

;; Use the above
#_(do
  ((add-message-m "First") (:messages test-game-state))
  ((domonad state-m [_ (add-message-m "First") rv (add-message-m "Second")] rv) (:messages test-game-state))
  ;; Use this with the larger game-state
  ;; with-state-field is a lot like zoom in Haskell lenses
  ((with-state-field :messages (add-message-m "First")) test-game-state))

(defn move-player-m
  "State player: Moves the player the specified dx/dy distance.
   Can be called on the game-state itself using with-state-field."
  [dx dy]
  (domonad state-m
    [_       (update-val :x (partial + dx))
     _       (update-val :y (partial + dy))]
     ;; TODO: Return nil if we couldn't move, or true if no error?
     ;; How do we get an error message in there?
     (if (> dx 65) nil true)))

;; Use the above
#_((move-player-m 1 1) (:player test-game-state))


;; Test multiple calls to sub-segments of the game state
(defn gs-move-player-m
  "State game: Moves the player the specified delta and adds a message
   that the player was moved."
  [dx dy]
  (domonad state-m
    [_ (with-state-field :player   (move-player-m dx dy))
     _ (with-state-field :messages (add-message-m (str "Moved by " dx "," dy)))]
    ;; No return value
    true))

;; Use the above
#_((gs-move-player-m 1 1) test-game-state)

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
    "Worked"))


;; Use the above
#_((gs-move-player-m-v2 1 1) test-game-state)

(defn gs-move-player-m-v3
  "State game: Moves the player the specified delta and adds a message
   that the player was moved."
  [dx dy]
  ;; Test out the state monad combined with the maybe monad. If a step
  ;; returns nil, no further computations are performed, but that state
  ;; is kept as of the nil return. This doesn't seem to be a useful thing.
  (domonad (maybe-t state-m)
    [_ (zoom :player   (move-player-m dx dy))
     _ (zoom :messages (add-message-m (str "Moved by " dx "," dy)))]
    ;; No return value
    "Worked v3"))

;; Use the above
#_((gs-move-player-m-v3 1 1) test-game-state)
#_((gs-move-player-m-v3 400 4) test-game-state) ; ==> nil but 402 on the X in the state

(defn gs-move-player-m-v4
  "State game: Moves the player the specified delta and adds a message
   that the player was moved."
  [dx dy]
  ;; This is like v3 but with the monad stack being state maybe instead of
  ;; maybe state. This seems to do nothing different than just having plain
  ;; state monad.
  (domonad (state-t maybe-m)
    [_ (zoom :player   (move-player-m dx dy))
     _ (zoom :messages (add-message-m (str "Moved by " dx "," dy)))]
    ;; No return value
    "Worked v4"))
;; Same tests as v3 above.


(defn gs-test-let-m
  "State game: Test the :let, :when, :cond and :if items in domonad."
  [dx dy]
  (dostate
    [_ (zoom :player   (move-player-m dx dy))
     x (zoom :player (fetch-val :x))
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
     ;; If the when is met, monad continues. Otherwise, the whole thing
     ;; returns nil instead of the usual [value state]. However, if we define
     ;; the m-zero on the state monad, then we get a proper monadic return of
     ;; nil, and the state as of the :when clause. I'm not sure this is a good
     ;; idea or not.
     :when (< x 70)
     _ (zoom :messages (add-message-m (str "Thank you for playing: " xplusy)))
     _ (zoom :messages (add-message-m "THE END"))]
    ;; Use our let in the return values
    (* xplusy xplus1)))

;; Use the above in all possible cases
#_(do
  ((gs-test-let-m 1 1) test-game-state)
  ((gs-test-let-m 4 4) test-game-state)
  ((gs-test-let-m 4 2) test-game-state)
  ((gs-test-let-m 4 -2) test-game-state)
  ((gs-test-let-m 99 4) test-game-state)) ; ==> nil or [nil state]


;; -------------------------------------------------------------------------------

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


;; m-until ---------------------------------------------------------------

(defn ɣ•add-hp
  "State game-state: Adds dhp to the player's HP.
   Returns true if the players new HP is < 2x max HP."
  [dhp]
  (dostate
    [_ (update-in-val [:player :hp] #(+ dhp %))
     newhp (fetch-in-val [:player :hp])
     maxhp (fetch-in-val [:player :hp-max])
     _ (<- (println "New hp: " newhp))]
    (< newhp (* 2 maxhp))))

;; Test the above
#_((ɣ•add-hp 13) test-game-state)

(defn ɣ•add-hp-m-until
  "State game-state: Adds n hp by adding one hp multiple times using
   state-m-until, to learn how state-m-until works."
  [n]
  (dostate
    [_ (state-m-until
         #(>= % n) ; p used in (p x)
         (fn [x] (domonad [_ (ɣ•add-hp 1)] (inc x))) ; f - add an hp and increment x
         0)] ; x - start at 0 and count up to n
    true))

;; Test the above
#_((ɣ•add-hp-m-until 13) test-game-state)


(defn ɣ•add-hp-m-until'
  "State game-state: Adds n hp by adding one hp multiple times using
   state-m-until, to learn how state-m-until works."
  [n]
  (µ•repeat n (ɣ•add-hp 1)))

(defn ɣ•add-hp-m-until''
  "State game-state: Adds n hp by adding one hp multiple times using
   state-m-until, to learn how state-m-until works."
  [n]
  (µ•repeat-until n (ɣ•add-hp 1)))

;; Test the above
#_(do
  ((ɣ•add-hp-m-until'  13) test-game-state)  ; -> :hp is 23
  ((ɣ•add-hp-m-until'' 13) test-game-state)) ; -> :hp is 20
