;; Copyright 2016 Douglas P. Fields, Jr.
;; symbolics _at_ lisp.engineer
;; https://symbolics.lisp.engineer/
;; https://twitter.com/LispEngineer
;; https://github.com/LispEngineer
;;;; Aiband - The Artificial Intelligence Roguelike

;;;; Message-log related functionality

(ns aiband.messages
  (:require [clojure.algo.monads :as µ
             ;; We specifically don't want update-val and update-state as we
             ;; made better versions of these
             :refer [domonad with-state-field fetch-val]]
            [aiband.monads :as aµ :refer :all :reload true]))

(defn create
  "Sets up our messages list.
   :initial - The ID of the first message in the list. IDs are monotonically increasing.
   :final - The ID of the final message in the list. If the ID is the same as
            initial, then there are no messages in the list.
   :text - The messages themselves. Message with ID x is located at vector index
           (x - initial - 1). This means that the first real message will have ID 1."
  []
  {:initial 0 :final 0 :text []})

(defn add
  "Non-monadic: Adds a message to a messages list set up by create.
   Takes the old messages list and returns a new one."
  ;; TODO: Reduce the initial so we don't have more than the
  ;; maximum number of desired messages in our history.
  [messages new-msg]
  (let [{:keys [initial final text]} messages
        new-final (+ final 1)
        new-text (conj text new-msg)
        new-initial initial]
    {:initial new-initial :final new-final :text new-text}))

(defn µ•add
  "State messages: Adds a message to the messages map of game-state. 
   Can be called on the game-state itself using with-state-field.
   Returns current number of messages."
  [message]
  (dostate
    [_       (update-val :text  conj message)
     _       (update-val :final inc)
     final   (fetch-val  :final)
     initial (fetch-val  :initial)]
    ;; Return current number of messages
    (- final initial)))

(defn ɣ•add
  "State game-state: Adds a message to the game state. Returns number of
   messages in the game state now."
  [message]
  (dostate
    [retval (» :messages µ•add message)] 
    retval))

;; Test the above
#_((aiband.messages/ɣ•add "Hello, Doug") aiband.game/test-game-state) 
