;; Copyright 2016 Douglas P. Fields, Jr.
;; symbolics _at_ lisp.engineer
;; https://symbolics.lisp.engineer/
;; https://twitter.com/LispEngineer
;; https://github.com/LispEngineer
;;;; Aiband - The Artificial Intelligence Roguelike

;;;; Message-log related functionality

(ns aiband.messages)

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
  "Adds a message to a messages list set up by create-messages.
   Takes the old messages list and returns a new one."
  ;; TODO: Reduce the initial so we don't have more than the
  ;; maximum number of desired messages in our history.
  [messages new-msg]
  (let [{:keys [initial final text]} messages
        new-final (+ final 1)
        new-text (conj text new-msg)
        new-initial initial]
    {:initial new-initial :final new-final :text new-text}))

